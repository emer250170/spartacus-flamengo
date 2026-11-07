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
import java.util.Collections;
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
						update(pk);
					}
					catch (final ReflectiveOperationException e)
					{
						throw new IllegalStateException(e);
					}
				}
			});
			LOG.info("Busca atualizada para o produto {} (foto nova)", code);
		}
		catch (final Exception e)
		{
			LOG.warn("Não foi possível atualizar a busca do produto {}: {}", code, e.toString());
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
		final String configName = StringUtils.defaultIfBlank(
				configurationService.getConfiguration().getString("flamengo.produtos.imagem.reindexar.config"), "electronicsIndex");
		final Object configService = applicationContext.getBean("facetSearchConfigService");
		final Object facetSearchConfig = configService.getClass().getMethod("getConfiguration", String.class).invoke(configService,
				configName);
		final Object indexConfig = facetSearchConfig.getClass().getMethod("getIndexConfig").invoke(facetSearchConfig);
		final Map<?, ?> types = (Map<?, ?>) indexConfig.getClass().getMethod("getIndexedTypes").invoke(indexConfig);
		Object indexedType = null;
		for (final Object type : types.values())
		{
			if ("Product".equals(type.getClass().getMethod("getCode").invoke(type)))
			{
				indexedType = type;
				break;
			}
		}
		if (indexedType == null)
		{
			throw new IllegalStateException("tipo Product não encontrado em " + configName);
		}
		final Object indexer = applicationContext.getBean("indexerService");
		for (final Method method : indexer.getClass().getMethods())
		{
			if ("updateTypeIndex".equals(method.getName()) && method.getParameterCount() == 3
					&& method.getParameterTypes()[2].isAssignableFrom(java.util.List.class))
			{
				method.invoke(indexer, facetSearchConfig, indexedType, Collections.singletonList(pk));
				return;
			}
		}
		throw new NoSuchMethodException("indexerService.updateTypeIndex(config, tipo, pks)");
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
