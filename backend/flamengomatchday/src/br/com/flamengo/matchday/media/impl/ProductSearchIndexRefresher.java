package br.com.flamengo.matchday.media.impl;

import de.hybris.platform.core.PK;
import de.hybris.platform.core.Registry;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.session.Session;
import de.hybris.platform.servicelayer.session.SessionExecutionBody;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.servicelayer.user.UserService;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;


/**
 * Foto nova de jogo ou experiência (07/11): a vitrine da home pega a foto pela busca (Solr), que guarda o
 * endereço da foto. Quando a foto é regravada o endereço muda e a busca ficava com o antigo (404) até o próximo
 * índice: a home mostrava a imagem padrão. Aqui, alguns segundos depois de gravar (o tempo de a gravação chegar
 * ao banco), o índice é atualizado só para aquele produto (nunca as poltronas, que não ficam na busca).
 * <p>
 * Chaves: {@code flamengo.produtos.imagem.reindexar} (padrão true), {@code flamengo.produtos.imagem.reindexar.config}
 * (padrão electronicsIndex) e {@code flamengo.produtos.imagem.reindexar.segundos} (padrão 10). Usa o indexador da
 * busca pelo nome dos beans (sem dependência de compilação da solrfacetsearch); se algo falhar, só registra no log.
 * <p>
 * Produto apagado (08/11): {@link #removeLater(Collection)} tira os produtos da busca da loja logo depois de
 * apagados (Backoffice, Claude, HAC). Sem isso a categoria e a busca continuavam listando o produto até o índice
 * completo da madrugada e a loja mostrava "Product with code ... not found!" ao abrir a listagem. Chamado pelo
 * {@link ProductSearchRemovalListener}. Chaves: {@code flamengo.produtos.busca.remover} (padrão true),
 * {@code flamengo.produtos.busca.remover.config} (padrão electronicsIndex; vários separados por vírgula) e
 * {@code flamengo.produtos.busca.remover.segundos} (padrão 2).
 */
public class ProductSearchIndexRefresher implements ApplicationContextAware, DisposableBean
{
	private static final Logger LOG = LoggerFactory.getLogger(ProductSearchIndexRefresher.class);

	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
		final Thread t = new Thread(r, "flamengo-reindexar-foto");
		t.setDaemon(true);
		return t;
	});

	private ApplicationContext applicationContext;
	private ConfigurationService configurationService;
	private SessionService sessionService;
	private UserService userService;

	/** Agenda a atualização do índice do produto (só produto do catálogo Online, que é o da busca) */
	public void refreshLater(final ProductModel product)
	{
		if (product == null || product.getPk() == null
				|| !configurationService.getConfiguration().getBoolean("flamengo.produtos.imagem.reindexar", true)
				|| product.getCatalogVersion() == null || !"Online".equals(product.getCatalogVersion().getVersion()))
		{
			return;
		}
		final PK pk = product.getPk();
		final String code = product.getCode();
		final String tenant = Registry.getCurrentTenant().getTenantID();
		final long delay = Math.max(1, configurationService.getConfiguration().getLong("flamengo.produtos.imagem.reindexar.segundos", 10));
		executor.schedule(() -> run(tenant, pk, code), delay, TimeUnit.SECONDS);
	}

	protected void run(final String tenant, final PK pk, final String code)
	{
		executeAsAdmin(tenant, () -> update(pk), "Busca atualizada para o produto " + code + " (foto nova)",
				"Não foi possível atualizar a busca do produto " + code);
	}

	/**
	 * Agenda a retirada dos produtos apagados da busca da loja. Pode receber PKs de produtos que nunca estiveram na
	 * busca (Staged, poltronas): para o Solr é só uma exclusão que não encontra nada.
	 */
	public void removeLater(final Collection<PK> pks)
	{
		if (pks == null || pks.isEmpty()
				|| !configurationService.getConfiguration().getBoolean("flamengo.produtos.busca.remover", true))
		{
			return;
		}
		final List<PK> copy = new ArrayList<>(pks);
		final String tenant = Registry.getCurrentTenant().getTenantID();
		final long delay = Math.max(0, configurationService.getConfiguration().getLong("flamengo.produtos.busca.remover.segundos", 2));
		executor.schedule(() -> executeAsAdmin(tenant, () -> remove(copy),
				"Busca da loja: " + copy.size() + " produto(s) apagado(s) retirado(s) " + copy,
				"Não foi possível tirar da busca os produtos apagados " + copy), delay, TimeUnit.SECONDS);
	}

	/** Ação feita como administrador numa sessão própria (linha de execução do agendador) */
	@FunctionalInterface
	protected interface SearchAction
	{
		void execute() throws ReflectiveOperationException;
	}

	protected void executeAsAdmin(final String tenant, final SearchAction action, final String okMessage, final String failMessage)
	{
		Session session = null;
		try
		{
			Registry.setCurrentTenantByID(tenant);
			session = sessionService.createNewSession();
			sessionService.executeInLocalView(new SessionExecutionBody()
			{
				@Override
				public void executeWithoutResult()
				{
					userService.setCurrentUser(userService.getAdminUser());
					try
					{
						action.execute();
					}
					catch (final ReflectiveOperationException e)
					{
						throw new IllegalStateException(e);
					}
				}
			});
			LOG.info(okMessage);
		}
		catch (final Exception e)
		{
			LOG.warn("{}: {}", failMessage, e.toString());
		}
		finally
		{
			if (session != null)
			{
				try
				{
					sessionService.closeSession(session);
				}
				catch (final RuntimeException e)
				{
					LOG.debug("sessão já fechada", e);
				}
			}
			Registry.unsetCurrentTenant();
		}
	}

	/** indexerService.updateTypeIndex(facetSearchConfig, indexedType "Product", [pk]) por reflexão */
	protected void update(final PK pk) throws ReflectiveOperationException
	{
		updateNow(Collections.singletonList(pk));
	}

	/**
	 * Atualiza agora, na linha de execução de quem chama, o índice da loja só destes produtos (PKs do catálogo Online;
	 * nunca poltronas). Usado pela atualização da vitrine pedida pelo Claude (FlaSearchRefresh, 09/11). Quem chama já
	 * deve estar numa sessão de administrador.
	 */
	public void updateNow(final List<PK> pks) throws ReflectiveOperationException
	{
		if (pks == null || pks.isEmpty())
		{
			return;
		}
		final String configName = StringUtils.defaultIfBlank(
				configurationService.getConfiguration().getString("flamengo.produtos.imagem.reindexar.config"), "electronicsIndex");
		final Object facetSearchConfig = facetSearchConfig(configName);
		final Object indexedType = productIndexedType(facetSearchConfig, configName);
		for (int i = 0; i < pks.size(); i += 200)
		{
			invokeIndexer("updateTypeIndex", facetSearchConfig, indexedType, new ArrayList<>(pks.subList(i, Math.min(pks.size(), i + 200))));
		}
	}

	/**
	 * indexerService.deleteTypeIndex(facetSearchConfig, indexedType "Product", pks) por reflexão, em cada índice de
	 * {@code flamengo.produtos.busca.remover.config}, em lotes de 200 (limite de cláusulas do Solr)
	 */
	protected void remove(final List<PK> pks) throws ReflectiveOperationException
	{
		final String configNames = StringUtils.defaultIfBlank(
				configurationService.getConfiguration().getString("flamengo.produtos.busca.remover.config"), "electronicsIndex");
		for (final String configName : StringUtils.split(configNames, ','))
		{
			final String name = configName.trim();
			if (name.isEmpty())
			{
				continue;
			}
			final Object facetSearchConfig = facetSearchConfig(name);
			final Object indexedType = productIndexedType(facetSearchConfig, name);
			for (int i = 0; i < pks.size(); i += 200)
			{
				invokeIndexer("deleteTypeIndex", facetSearchConfig, indexedType, new ArrayList<>(pks.subList(i, Math.min(pks.size(), i + 200))));
			}
		}
	}

	protected Object facetSearchConfig(final String configName) throws ReflectiveOperationException
	{
		final Object configService = applicationContext.getBean("facetSearchConfigService");
		return configService.getClass().getMethod("getConfiguration", String.class).invoke(configService, configName);
	}

	protected Object productIndexedType(final Object facetSearchConfig, final String configName) throws ReflectiveOperationException
	{
		final Object indexConfig = facetSearchConfig.getClass().getMethod("getIndexConfig").invoke(facetSearchConfig);
		final Map<?, ?> types = (Map<?, ?>) indexConfig.getClass().getMethod("getIndexedTypes").invoke(indexConfig);
		for (final Object type : types.values())
		{
			if ("Product".equals(type.getClass().getMethod("getCode").invoke(type)))
			{
				return type;
			}
		}
		throw new IllegalStateException("tipo Product não encontrado em " + configName);
	}

	/** indexerService.&lt;método&gt;(config, tipo, pks): a versão que recebe a lista de PKs */
	protected void invokeIndexer(final String methodName, final Object facetSearchConfig, final Object indexedType, final List<PK> pks)
			throws ReflectiveOperationException
	{
		final Object indexer = applicationContext.getBean("indexerService");
		for (final Method method : indexer.getClass().getMethods())
		{
			if (methodName.equals(method.getName()) && method.getParameterCount() == 3
					&& method.getParameterTypes()[2].isAssignableFrom(java.util.List.class))
			{
				method.invoke(indexer, facetSearchConfig, indexedType, pks);
				return;
			}
		}
		throw new NoSuchMethodException("indexerService." + methodName + "(config, tipo, pks)");
	}

	@Override
	public void destroy()
	{
		executor.shutdownNow();
	}

	@Override
	public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException
	{
		this.applicationContext = applicationContext;
	}

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}

	public void setSessionService(final SessionService sessionService)
	{
		this.sessionService = sessionService;
	}

	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}
}
