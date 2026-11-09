package br.com.flamengo.matchday.mcp;

import br.com.flamengo.matchday.media.impl.ProductSearchIndexRefresher;

import de.hybris.platform.catalog.enums.ArticleApprovalStatus;
import de.hybris.platform.core.PK;
import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.cronjob.enums.CronJobResult;
import de.hybris.platform.cronjob.model.CronJobModel;
import de.hybris.platform.servicelayer.cronjob.CronJobService;
import de.hybris.platform.servicelayer.interceptor.InterceptorContext;
import de.hybris.platform.servicelayer.interceptor.InterceptorException;
import de.hybris.platform.servicelayer.interceptor.PrepareInterceptor;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Atualização da vitrine pedida por um gestor pelo Claude (MCP, 09/11), tipo FlaSearchRefresh. A busca da loja (Solr)
 * só mostra produto novo ou alterado depois da próxima indexação; aqui o gestor pede e o Commerce atualiza na hora.
 * <ul>
 * <li>Com produtos: atualiza o índice só desses produtos (catálogo Online), como a foto nova já faz
 * ({@link ProductSearchIndexRefresher#updateNow}). Avisa o que impede o produto de aparecer: só no Staged (falta
 * sincronizar), não aprovado, sem preço. Poltronas e setores de jogo nunca entram na busca.</li>
 * <li>Sem produtos: roda o cronjob de update do índice (só o incremental, nunca o completo); se ele já estiver rodando,
 * espera terminar e roda de novo.</li>
 * </ul>
 * Qualquer gestor (loja, jogos, sócio) pode pedir: só atualiza a busca, não muda dado de produto. Chaves:
 * {@code flamengo.mcp.vitrine.ativo}, {@code .cronjob} (padrão update-electronicsIndex-cronJob), {@code .catalogo}
 * (padrão flamengoProductCatalog) e {@code .maxProdutos} (padrão 20).
 */
public class FlaSearchRefreshInterceptor implements PrepareInterceptor<ItemModel>
{
	private static final Logger LOG = LoggerFactory.getLogger(FlaSearchRefreshInterceptor.class);

	static final String TYPE = "FlaSearchRefresh";
	static final String CHAVE_ATIVO = "flamengo.mcp.vitrine.ativo";
	static final String CHAVE_CRONJOB = "flamengo.mcp.vitrine.cronjob";
	static final String CHAVE_CATALOGO = "flamengo.mcp.vitrine.catalogo";
	static final String CHAVE_MAX = "flamengo.mcp.vitrine.maxProdutos";
	/** Jogo (JOGO-AAAAMMDD-ADV) entra na busca; setores e poltronas (código do jogo + sufixo) não */
	private static final String JOGO = "^JOGO-\\d{8}-[A-Za-z0-9]+$";
	private static final int ESPERA_CRONJOB_SEGUNDOS = 120;

	private McpSupport mcpSupport;
	private CronJobService cronJobService;
	private ProductSearchIndexRefresher searchIndexRefresher;

	@Override
	public void onPrepare(final ItemModel item, final InterceptorContext ctx) throws InterceptorException
	{
		if (!ctx.isNew(item))
		{
			return;
		}
		final ModelService ms = ctx.getModelService();
		try
		{
			if (!mcpSupport.flag(CHAVE_ATIVO, true))
			{
				throw new IllegalArgumentException("A atualização da vitrine pelo Claude está desligada no servidor (" + CHAVE_ATIVO + ").");
			}
			final UserModel user = mcpSupport.currentUser();
			final String area = mcpSupport.resolveArea(ms.getAttributeValue(item, "area"), user);
			final List<String> codes = codes(ms.getAttributeValue(item, "products"));
			final int max = (int) mcpSupport.number(CHAVE_MAX, 20);
			if (codes.size() > max)
			{
				throw new IllegalArgumentException("No máximo " + max + " produtos por pedido; para atualizar tudo, peça sem produtos.");
			}
			ms.setAttributeValue(item, "products", codes.isEmpty() ? null : String.join(", ", codes));
			ms.setAttributeValue(item, "area", area);
			ms.setAttributeValue(item, "requestedBy", user.getUid());
			ms.setAttributeValue(item, "status", "PROCESSANDO");
			ms.setAttributeValue(item, "message", codes.isEmpty() ? "Atualizando o índice da loja (" + cronJobName() + ")"
					: "Atualizando a busca de " + String.join(", ", codes));
		}
		catch (final IllegalArgumentException e)
		{
			throw new InterceptorException(e.getMessage());
		}
		mcpSupport.afterCommit(TYPE, ms.getAttributeValue(item, "code"), this::run);
	}

	/** Depois da gravação, como administrador: produtos pedidos ou cronjob de update */
	protected void run(final ItemModel item)
	{
		final ModelService ms = mcpSupport.getModelService();
		final String code = ms.getAttributeValue(item, "code");
		try
		{
			final List<String> codes = codes(ms.getAttributeValue(item, "products"));
			if (codes.isEmpty())
			{
				runCronJob(item);
			}
			else
			{
				refreshProducts(item, codes);
			}
		}
		catch (final Exception e)
		{
			LOG.error("Falha na atualização da vitrine " + code, e);
			finish(item, "ERRO", "Falha ao atualizar a vitrine: " + e.getMessage());
		}
	}

	protected void refreshProducts(final ItemModel item, final List<String> codes) throws ReflectiveOperationException
	{
		final String catalog = mcpSupport.config(CHAVE_CATALOGO, "flamengoProductCatalog");
		final List<PK> pks = new ArrayList<>();
		final List<String> ok = new ArrayList<>();
		final List<String> notes = new ArrayList<>();
		for (final String code : codes)
		{
			if (code.toUpperCase().startsWith("JOGO-") && !code.matches(JOGO))
			{
				notes.add(code + ": setor ou poltrona de jogo não aparece na busca (use o código do jogo, JOGO-AAAAMMDD-ADV)");
				continue;
			}
			final ProductModel online = product(code, catalog, "Online");
			if (online == null)
			{
				notes.add(code + (product(code, catalog, "Staged") != null
						? ": existe só no Staged; falta sincronizar Staged → Online (o gestor da loja não sincroniza; peça ao administrador)"
						: ": não existe no catálogo " + catalog));
				continue;
			}
			pks.add(online.getPk());
			ok.add(code);
			if (online.getApprovalStatus() != ArticleApprovalStatus.APPROVED)
			{
				notes.add(code + ": não está aprovado, então a loja não mostra mesmo depois da atualização");
			}
			if (!hasPrice(online))
			{
				notes.add(code + ": sem preço, então a loja pode não mostrar");
			}
		}
		if (pks.isEmpty())
		{
			finish(item, "ERRO", "Nenhum produto atualizado. " + String.join("; ", notes) + ".");
			return;
		}
		final long start = System.currentTimeMillis();
		searchIndexRefresher.updateNow(pks);
		finish(item, "CONCLUIDO", "Vitrine atualizada para " + ok.size() + " produto(s): " + String.join(", ", ok) + " ("
				+ seconds(start) + " s). A loja já mostra na busca e nas listagens; se a página estava aberta, recarregar."
				+ (notes.isEmpty() ? "" : " Avisos: " + String.join("; ", notes) + "."));
	}

	protected void runCronJob(final ItemModel item)
	{
		final String name = cronJobName();
		final CronJobModel cronJob = cronJobService.getCronJob(name);
		boolean waited = false;
		for (int i = 0; i < ESPERA_CRONJOB_SEGUNDOS && cronJobService.isRunning(cronJob); i++)
		{
			waited = true;
			McpSupport.sleep(1000);
			mcpSupport.getModelService().refresh(cronJob);
		}
		if (cronJobService.isRunning(cronJob))
		{
			finish(item, "ERRO", "O " + name + " está rodando há mais de " + ESPERA_CRONJOB_SEGUNDOS + " s; tente de novo em alguns minutos.");
			return;
		}
		final long start = System.currentTimeMillis();
		cronJobService.performCronJob(cronJob, true);
		mcpSupport.getModelService().refresh(cronJob);
		final boolean success = cronJob.getResult() == CronJobResult.SUCCESS;
		finish(item, success ? "CONCLUIDO" : "ERRO", (success ? "Índice da loja atualizado (" : "O índice terminou com erro (")
				+ name + ", resultado " + cronJob.getResult() + ", " + seconds(start) + " s"
				+ (waited ? "; esperou a indexação que já estava rodando" : "") + ")."
				+ (success ? " Produtos criados ou alterados desde a última indexação já aparecem na busca e nas listagens." : " Veja o log do cronjob no Backoffice."));
	}

	protected ProductModel product(final String code, final String catalog, final String version)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery("SELECT {p.pk} FROM {Product AS p JOIN CatalogVersion AS cv "
				+ "ON {p.catalogVersion}={cv.pk} JOIN Catalog AS c ON {cv.catalog}={c.pk}} "
				+ "WHERE {p.code}=?code AND {c.id}=?catalog AND {cv.version}=?version");
		query.addQueryParameter("code", code);
		query.addQueryParameter("catalog", catalog);
		query.addQueryParameter("version", version);
		final List<ProductModel> found = flexibleSearchService().<ProductModel> search(query).getResult();
		return found.isEmpty() ? null : found.get(0);
	}

	protected boolean hasPrice(final ProductModel product)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery(
				"SELECT COUNT({pk}) FROM {PriceRow} WHERE {product}=?product OR {productId}=?code");
		query.addQueryParameter("product", product);
		query.addQueryParameter("code", product.getCode());
		query.setResultClassList(Collections.singletonList(Integer.class));
		final List<Integer> count = flexibleSearchService().<Integer> search(query).getResult();
		return !count.isEmpty() && count.get(0) != null && count.get(0) > 0;
	}

	protected void finish(final ItemModel item, final String status, final String message)
	{
		final ModelService ms = mcpSupport.getModelService();
		ms.refresh(item);
		ms.setAttributeValue(item, "status", status);
		ms.setAttributeValue(item, "message", StringUtils.abbreviate(message, 4000));
		ms.setAttributeValue(item, "finishedAt", new Date());
		ms.save(item);
	}

	protected String cronJobName()
	{
		return mcpSupport.config(CHAVE_CRONJOB, "update-electronicsIndex-cronJob");
	}

	private FlexibleSearchService flexibleSearchService()
	{
		return mcpSupport.getFlexibleSearchService();
	}

	/** "A, B; C" -> [A, B, C] (sem repetidos) */
	static List<String> codes(final String text)
	{
		final Set<String> out = new LinkedHashSet<>();
		for (final String part : StringUtils.defaultString(text).split("[,;\\s]+"))
		{
			if (!part.trim().isEmpty())
			{
				out.add(part.trim());
			}
		}
		return new ArrayList<>(out);
	}

	private static long seconds(final long start)
	{
		return Math.max(1, Math.round((System.currentTimeMillis() - start) / 1000.0));
	}

	public void setMcpSupport(final McpSupport mcpSupport)
	{
		this.mcpSupport = mcpSupport;
	}

	public void setCronJobService(final CronJobService cronJobService)
	{
		this.cronJobService = cronJobService;
	}

	public void setSearchIndexRefresher(final ProductSearchIndexRefresher searchIndexRefresher)
	{
		this.searchIndexRefresher = searchIndexRefresher;
	}
}
