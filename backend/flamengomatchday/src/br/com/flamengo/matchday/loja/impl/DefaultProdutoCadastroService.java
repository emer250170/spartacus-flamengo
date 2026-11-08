package br.com.flamengo.matchday.loja.impl;

import br.com.flamengo.matchday.loja.ProdutoCadastroService;
import br.com.flamengo.matchday.media.ProductImageService;
import br.com.flamengo.matchday.media.impl.ProductSearchIndexRefresher;
import de.hybris.platform.basecommerce.enums.InStockStatus;
import de.hybris.platform.catalog.CatalogVersionService;
import de.hybris.platform.catalog.enums.ArticleApprovalStatus;
import de.hybris.platform.catalog.model.CatalogVersionModel;
import de.hybris.platform.category.model.CategoryModel;
import de.hybris.platform.core.PK;
import de.hybris.platform.core.Registry;
import de.hybris.platform.core.Tenant;
import de.hybris.platform.core.model.media.MediaModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.UserGroupModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.europe1.model.PriceRowModel;
import de.hybris.platform.ordersplitting.WarehouseService;
import de.hybris.platform.ordersplitting.model.StockLevelModel;
import de.hybris.platform.ordersplitting.model.WarehouseModel;
import de.hybris.platform.product.UnitService;
import de.hybris.platform.search.restriction.SearchRestrictionService;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.i18n.CommonI18NService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.session.SessionExecutionBody;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.servicelayer.user.UserService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Cadastro simples de produto da loja (08/11). Ver {@link ProdutoCadastroService}. Atributos novos do produto são lidos e
 * gravados pelo nome (modelService), sem depender do ProductModel gerado.
 */
public class DefaultProdutoCadastroService implements ProdutoCadastroService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultProdutoCadastroService.class);
	private static final String SEP = " › ";
	private static final int FOTOS = 4;

	private ConfigurationService configurationService;
	private ModelService modelService;
	private FlexibleSearchService flexibleSearchService;
	private CatalogVersionService catalogVersionService;
	private UserService userService;
	private SessionService sessionService;
	private SearchRestrictionService searchRestrictionService;
	private CommonI18NService commonI18NService;
	private UnitService unitService;
	private WarehouseService warehouseService;
	private ProductImageService productImageService;
	private ProductSearchIndexRefresher searchIndexRefresher;

	// ------------------------------------------------------------ chaves

	@Override
	public boolean ativo()
	{
		return configurationService.getConfiguration().getBoolean(ATIVO, true);
	}

	protected String config(final String key, final String fallback)
	{
		return StringUtils.defaultIfBlank(configurationService.getConfiguration().getString(key), fallback);
	}

	protected List<String> lista(final String key, final String fallback)
	{
		return Arrays.stream(config(key, fallback).split(",")).map(String::trim).filter(StringUtils::isNotBlank)
				.collect(Collectors.toList());
	}

	protected String catalogo()
	{
		return config("flamengo.loja.cadastro.catalogo", "flamengoProductCatalog");
	}

	protected String versao()
	{
		return config("flamengo.loja.cadastro.versao", "Online");
	}

	protected String deposito()
	{
		return config("flamengo.loja.cadastro.deposito", "warehouse_s");
	}

	protected String moeda()
	{
		return config("flamengo.loja.cadastro.moeda", "BRL");
	}

	// ------------------------------------------------------------ produto novo

	@Override
	public boolean usuarioDoCadastro()
	{
		final UserModel user = userService.getCurrentUser();
		if (user == null || userService.isAnonymousUser(user))
		{
			return false;
		}
		for (final String uid : lista("flamengo.loja.cadastro.grupos", "lojamanagerrole"))
		{
			try
			{
				final UserGroupModel group = userService.getUserGroupForUID(uid);
				if (group != null && userService.isMemberOfGroup(user, group))
				{
					return true;
				}
			}
			catch (final RuntimeException e)
			{
				LOG.debug("Grupo {} não existe", uid);
			}
		}
		return false;
	}

	@Override
	public void valoresIniciais(final ProductModel product)
	{
		if (product.getCatalogVersion() == null)
		{
			try
			{
				product.setCatalogVersion(catalogVersionService.getCatalogVersion(catalogo(), versao()));
			}
			catch (final RuntimeException e)
			{
				LOG.warn("Catálogo {}:{} não encontrado para o produto novo", catalogo(), versao());
			}
		}
		product.setApprovalStatus(ArticleApprovalStatus.APPROVED);
		if (product.getUnit() == null)
		{
			try
			{
				product.setUnit(unitService.getUnitForCode(config("flamengo.loja.cadastro.unidade", "pieces")));
			}
			catch (final RuntimeException e)
			{
				LOG.warn("Unidade padrão do cadastro não encontrada");
			}
		}
	}

	// ------------------------------------------------------------ pendentes

	protected Object valor(final ProductModel product, final String attribute)
	{
		try
		{
			return modelService.getAttributeValue(product, attribute);
		}
		catch (final RuntimeException e)
		{
			return null;
		}
	}

	protected List<Object> pendentes(final ProductModel product)
	{
		final List<Object> out = new ArrayList<>();
		out.add(valor(product, PRECO_NOVO));
		out.add(valor(product, ESTOQUE_NOVO));
		for (int i = 0; i < FOTOS; i++)
		{
			out.add(valor(product, FOTO_NOVA + i));
		}
		return out;
	}

	@Override
	public boolean temPendente(final ProductModel product)
	{
		return product != null && pendentes(product).stream().anyMatch(Objects::nonNull);
	}

	@Override
	public void aplicarDepoisDeGravar(final ProductModel product)
	{
		if (!ativo() || !temPendente(product) || product.getCatalogVersion() == null)
		{
			return;
		}
		final String code = product.getCode();
		final PK catalogVersion = product.getCatalogVersion().getPk();
		final List<Object> esperado = pendentes(product).stream().map(v -> v instanceof MediaModel ? ((MediaModel) v).getPk() : v)
				.collect(Collectors.toList());
		final Tenant tenant = Registry.getCurrentTenant();
		final Thread thread = new Thread(() -> {
			Registry.setCurrentTenant(tenant);
			try
			{
				sessionService.executeInLocalView(new SessionExecutionBody()
				{
					@Override
					public void executeWithoutResult()
					{
						prepararSessao();
						// espera a gravação terminar: o produto existe e os pendentes no banco são os desta gravação
						ProductModel saved = null;
						for (int i = 0; i < 60 && saved == null; i++)
						{
							dormir(1000);
							final ProductModel found = buscarProduto(code, catalogVersion);
							if (found != null)
							{
								modelService.refresh(found);
								final List<Object> agora = pendentes(found).stream()
										.map(v -> v instanceof MediaModel ? ((MediaModel) v).getPk() : v).collect(Collectors.toList());
								if (agora.equals(esperado))
								{
									saved = found;
								}
								else if (agora.stream().allMatch(Objects::isNull))
								{
									LOG.info("Pendentes do produto {} já aplicados ou retirados; nada a fazer", code);
									return;
								}
							}
						}
						if (saved == null)
						{
							LOG.warn("Produto {} não foi gravado com os pendentes esperados; nada a fazer", code);
							return;
						}
						LOG.info("Cadastro do produto {}: {}", code, aplicar(saved));
					}
				});
			}
			catch (final Exception e)
			{
				LOG.error("Falha ao aplicar o cadastro do produto " + code, e);
			}
			finally
			{
				Registry.unsetCurrentTenant();
			}
		}, "fla-cadastro-" + code);
		thread.setDaemon(true);
		thread.start();
	}

	@Override
	public String aplicar(final ProductModel product)
	{
		final List<String> feito = new ArrayList<>();
		final Object preco = valor(product, PRECO_NOVO);
		if (preco instanceof Number)
		{
			gravarPreco(product, ((Number) preco).doubleValue());
			feito.add("preço " + preco);
		}
		final Object estoque = valor(product, ESTOQUE_NOVO);
		if (estoque instanceof Number)
		{
			gravarEstoque(product, ((Number) estoque).intValue());
			feito.add("estoque " + estoque);
		}
		final List<MediaModel> fotos = new ArrayList<>();
		for (int i = 0; i < FOTOS; i++)
		{
			final Object foto = valor(product, FOTO_NOVA + i);
			fotos.add(foto instanceof MediaModel ? (MediaModel) foto : null);
		}
		String fotosFeitas = null;
		if (fotos.stream().anyMatch(Objects::nonNull))
		{
			try
			{
				fotosFeitas = productImageService.setProductPhotos(product, fotos);
			}
			catch (final RuntimeException e)
			{
				LOG.warn("Fotos do produto {} não gravadas: {}", product.getCode(), e.getMessage(), e);
				fotosFeitas = "fotos não gravadas (" + e.getMessage() + ")";
			}
			feito.add(fotosFeitas);
		}
		modelService.refresh(product);
		modelService.setAttributeValue(product, PRECO_NOVO, null);
		modelService.setAttributeValue(product, ESTOQUE_NOVO, null);
		for (int i = 0; i < FOTOS; i++)
		{
			modelService.setAttributeValue(product, FOTO_NOVA + i, null);
		}
		modelService.save(product);
		if (fotosFeitas == null && searchIndexRefresher != null)
		{
			searchIndexRefresher.refreshLater(product);
		}
		return feito.isEmpty() ? "nada pendente" : String.join(", ", feito);
	}

	protected void gravarPreco(final ProductModel product, final double preco)
	{
		final List<PriceRowModel> linhas = linhasDePreco(product);
		if (linhas.isEmpty())
		{
			final PriceRowModel row = modelService.create(PriceRowModel.class);
			row.setProduct(product);
			row.setCatalogVersion(product.getCatalogVersion());
			row.setCurrency(commonI18NService.getCurrency(moeda()));
			row.setUnit(product.getUnit() != null ? product.getUnit()
					: unitService.getUnitForCode(config("flamengo.loja.cadastro.unidade", "pieces")));
			row.setPrice(Double.valueOf(preco));
			row.setNet(Boolean.FALSE);
			row.setMinqtd(Long.valueOf(1));
			row.setUnitFactor(Integer.valueOf(1));
			modelService.save(row);
			return;
		}
		for (final PriceRowModel row : linhas)
		{
			row.setPrice(Double.valueOf(preco));
			modelService.save(row);
		}
	}

	protected void gravarEstoque(final ProductModel product, final int quantidade)
	{
		final StockLevelModel atual = nivelDeEstoque(product);
		if (atual != null)
		{
			atual.setAvailable(quantidade);
			modelService.save(atual);
			return;
		}
		final WarehouseModel warehouse = warehouseService.getWarehouseForCode(deposito());
		final StockLevelModel level = modelService.create(StockLevelModel.class);
		level.setProductCode(product.getCode());
		level.setWarehouse(warehouse);
		level.setAvailable(quantidade);
		level.setReserved(0);
		level.setPreOrder(0);
		level.setMaxPreOrder(0);
		level.setMaxStockLevelHistoryCount(0);
		level.setInStockStatus(InStockStatus.NOTSPECIFIED);
		modelService.save(level);
	}

	// ------------------------------------------------------------ leitura (aba Cadastro)

	protected <T> T semRestricao(final Supplier<T> body)
	{
		return sessionService.executeInLocalView(new SessionExecutionBody()
		{
			@Override
			public Object execute()
			{
				searchRestrictionService.disableSearchRestrictions();
				return body.get();
			}
		});
	}

	protected List<PriceRowModel> linhasDePreco(final ProductModel product)
	{
		if (product == null || product.getPk() == null)
		{
			return Collections.emptyList();
		}
		return semRestricao(() -> {
			final FlexibleSearchQuery q = new FlexibleSearchQuery("SELECT {r.pk} FROM {PriceRow AS r JOIN Currency AS c ON {r.currency}={c.pk}} "
					+ "WHERE ({r.product}=?p OR {r.productId}=?code) AND {c.isocode}=?moeda AND {r.ug} IS NULL "
					+ "AND ({r.minqtd} IS NULL OR {r.minqtd}<=1) ORDER BY {r.pk}");
			q.addQueryParameter("p", product);
			q.addQueryParameter("code", product.getCode());
			q.addQueryParameter("moeda", moeda());
			return flexibleSearchService.<PriceRowModel> search(q).getResult();
		});
	}

	protected StockLevelModel nivelDeEstoque(final ProductModel product)
	{
		if (product == null || StringUtils.isBlank(product.getCode()))
		{
			return null;
		}
		return semRestricao(() -> {
			final FlexibleSearchQuery q = new FlexibleSearchQuery("SELECT {s.pk} FROM {StockLevel AS s JOIN Warehouse AS w "
					+ "ON {s.warehouse}={w.pk}} WHERE {s.productCode}=?code AND {w.code}=?dep");
			q.addQueryParameter("code", product.getCode());
			q.addQueryParameter("dep", deposito());
			final List<StockLevelModel> result = flexibleSearchService.<StockLevelModel> search(q).getResult();
			return result.isEmpty() ? null : result.get(0);
		});
	}

	@Override
	public Double precoAtual(final ProductModel product)
	{
		final List<PriceRowModel> linhas = linhasDePreco(product);
		return linhas.isEmpty() ? null : linhas.get(0).getPrice();
	}

	@Override
	public Integer estoqueAtual(final ProductModel product)
	{
		final StockLevelModel level = nivelDeEstoque(product);
		return level == null ? null : Integer.valueOf(level.getAvailable());
	}

	// ------------------------------------------------------------ categorias

	protected boolean oculta(final CategoryModel category)
	{
		final List<String> ocultas = lista("flamengo.loja.cadastro.categorias.ocultas",
				"flamengo_ingressos,tamanho-camisa,socio_torcedor,socio_torcedor_fora,flamengo_tamanho,setor,experiencias,"
						+ "flamengo_personalizacao,mengo_estudio");
		CategoryModel current = category;
		for (int depth = 0; depth < 8 && current != null; depth++)
		{
			if (ocultas.contains(current.getCode()))
			{
				return true;
			}
			current = pai(current);
		}
		return false;
	}

	protected CategoryModel pai(final CategoryModel category)
	{
		for (final CategoryModel parent : seguro(category.getSupercategories()))
		{
			if (Objects.equals(parent.getCatalogVersion(), category.getCatalogVersion()))
			{
				return parent;
			}
		}
		return null;
	}

	@Override
	public String caminho(final CategoryModel category)
	{
		if (category == null)
		{
			return "";
		}
		final List<String> partes = new ArrayList<>();
		CategoryModel current = category;
		for (int depth = 0; depth < 8 && current != null; depth++)
		{
			partes.add(0, StringUtils.defaultIfBlank(current.getName(), current.getCode()));
			current = pai(current);
		}
		return String.join(SEP, partes);
	}

	@Override
	public List<CategoryModel> categoriasDaLoja()
	{
		final CatalogVersionModel cv;
		try
		{
			cv = catalogVersionService.getCatalogVersion(catalogo(), versao());
		}
		catch (final RuntimeException e)
		{
			return Collections.emptyList();
		}
		final FlexibleSearchQuery q = new FlexibleSearchQuery("SELECT {pk} FROM {Category} WHERE {catalogVersion}=?cv");
		q.addQueryParameter("cv", cv);
		final List<CategoryModel> all = flexibleSearchService.<CategoryModel> search(q).getResult();
		final Map<CategoryModel, String> paths = new HashMap<>();
		for (final CategoryModel category : all)
		{
			if (!oculta(category))
			{
				paths.put(category, caminho(category));
			}
		}
		return paths.keySet().stream().sorted(Comparator.comparing(paths::get, String.CASE_INSENSITIVE_ORDER))
				.collect(Collectors.toList());
	}

	@Override
	public CategoryModel categoriaDaLoja(final ProductModel product)
	{
		if (product == null)
		{
			return null;
		}
		for (final CategoryModel category : seguro(product.getSupercategories()))
		{
			if (Objects.equals(category.getCatalogVersion(), product.getCatalogVersion()) && !oculta(category))
			{
				return category;
			}
		}
		return null;
	}

	@Override
	public void definirCategoriaDaLoja(final ProductModel product, final CategoryModel category)
	{
		final Set<CategoryModel> novas = new LinkedHashSet<>();
		if (category != null)
		{
			novas.add(category);
		}
		for (final CategoryModel existing : seguro(product.getSupercategories()))
		{
			// mantém as técnicas (tamanho, etc.) e as de outros catálogos; troca as da loja
			if (!Objects.equals(existing.getCatalogVersion(), product.getCatalogVersion()) || oculta(existing))
			{
				novas.add(existing);
			}
		}
		product.setSupercategories(new ArrayList<>(novas));
	}

	// ------------------------------------------------------------ apoio

	protected ProductModel buscarProduto(final String code, final PK catalogVersion)
	{
		final FlexibleSearchQuery q = new FlexibleSearchQuery("SELECT {pk} FROM {Product} WHERE {code}=?code AND {catalogVersion}=?cv");
		q.addQueryParameter("code", code);
		q.addQueryParameter("cv", catalogVersion);
		final List<ProductModel> result = flexibleSearchService.<ProductModel> search(q).getResult();
		return result.isEmpty() ? null : result.get(0);
	}

	protected void prepararSessao()
	{
		userService.setCurrentUser(userService.getAdminUser());
		searchRestrictionService.disableSearchRestrictions();
		catalogVersionService.setSessionCatalogVersions(catalogVersionService.getAllCatalogVersions());
	}

	protected static void dormir(final long ms)
	{
		try
		{
			Thread.sleep(ms);
		}
		catch (final InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
	}

	protected static <T> Collection<T> seguro(final Collection<T> values)
	{
		return values == null ? Collections.emptyList() : values;
	}

	// ------------------------------------------------------------ injeção

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	public void setCatalogVersionService(final CatalogVersionService catalogVersionService)
	{
		this.catalogVersionService = catalogVersionService;
	}

	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}

	public void setSessionService(final SessionService sessionService)
	{
		this.sessionService = sessionService;
	}

	public void setSearchRestrictionService(final SearchRestrictionService searchRestrictionService)
	{
		this.searchRestrictionService = searchRestrictionService;
	}

	public void setCommonI18NService(final CommonI18NService commonI18NService)
	{
		this.commonI18NService = commonI18NService;
	}

	public void setUnitService(final UnitService unitService)
	{
		this.unitService = unitService;
	}

	public void setWarehouseService(final WarehouseService warehouseService)
	{
		this.warehouseService = warehouseService;
	}

	public void setProductImageService(final ProductImageService productImageService)
	{
		this.productImageService = productImageService;
	}

	public void setSearchIndexRefresher(final ProductSearchIndexRefresher searchIndexRefresher)
	{
		this.searchIndexRefresher = searchIndexRefresher;
	}
}
