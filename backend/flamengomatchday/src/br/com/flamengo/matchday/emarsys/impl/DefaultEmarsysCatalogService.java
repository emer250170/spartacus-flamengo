package br.com.flamengo.matchday.emarsys.impl;

import br.com.flamengo.matchday.emarsys.EmarsysCatalogService;
import de.hybris.platform.basecommerce.enums.InStockStatus;
import de.hybris.platform.catalog.CatalogVersionService;
import de.hybris.platform.catalog.model.CatalogVersionModel;
import de.hybris.platform.catalog.model.classification.ClassificationClassModel;
import de.hybris.platform.category.model.CategoryModel;
import de.hybris.platform.core.model.media.MediaContainerModel;
import de.hybris.platform.core.model.media.MediaModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.europe1.model.PriceRowModel;
import de.hybris.platform.ordersplitting.model.StockLevelModel;
import de.hybris.platform.search.restriction.SearchRestrictionService;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.session.SessionExecutionBody;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.servicelayer.user.UserService;
import de.hybris.platform.variants.model.VariantCategoryModel;
import de.hybris.platform.variants.model.VariantProductModel;
import de.hybris.platform.variants.model.VariantValueCategoryModel;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Gera o catálogo de produtos no formato CSV do Emarsys (guia "Product catalog onboarding in CSV
 * format" e "CSV Guidelines" do SAP Emarsys).
 * <ul>
 * <li>Uma linha por produto base (sem variantes: a poltrona entra pelo jogo, o tamanho pela
 * camisa); os mesmos códigos que a loja manda no Web Extend.</li>
 * <li>{@code link}: página do produto na loja; jogo ({@code JOGO-…}) abre a jornada de matchday.</li>
 * <li>{@code image}: maior formato JPG/PNG/GIF até 1 MB do primeiro conjunto da galeria (sem WEBP).</li>
 * <li>{@code category}: caminhos completos das categorias ({@code A > B}), vários separados por
 * {@code |}; categorias de variante (tamanho, setor) ficam de fora.</li>
 * <li>{@code price}: menor preço BRL de lista (sem grupo de preço) do produto ou das variantes.</li>
 * <li>{@code available}: estoque em alguma variante (ou forçado em estoque); serviços do dia de jogo
 * (estacionamento, comida, personalização) sempre {@code false}, para não serem recomendados.</li>
 * <li>Campos em inglês com o sufixo {@code _en}; campo próprio {@code tipo}.</li>
 * </ul>
 * O formato do arquivo não deve mudar depois que o Emarsys começa a importar (colunas novas só no fim).
 */
public class DefaultEmarsysCatalogService implements EmarsysCatalogService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultEmarsysCatalogService.class);

	/** Ordem das colunas: não mudar (o Emarsys exige o mesmo formato; colunas novas só no fim). */
	static final List<String> HEADER = Collections.unmodifiableList(Arrays.asList("item", "title", "title_en", "link",
			"link_en", "image", "category", "category_en", "price", "available", "description", "description_en", "tipo"));

	private static final Locale PT = new Locale("pt");
	private static final Locale EN = Locale.ENGLISH;
	private static final long MAX_IMAGE_BYTES = 1024L * 1024L;
	private static final Pattern FORMAT_WIDTH = Pattern.compile("^(\\d+)");
	private static final Pattern TAGS = Pattern.compile("<[^>]+>");
	private static final Pattern SPACES = Pattern.compile("\\s+");
	private static final Set<String> SERVICE_TYPES = new LinkedHashSet<>(
			Arrays.asList("estacionamento", "alimentacao", "personalizacao"));

	private FlexibleSearchService flexibleSearchService;
	private CatalogVersionService catalogVersionService;
	private ConfigurationService configurationService;
	private SessionService sessionService;
	private UserService userService;
	private SearchRestrictionService searchRestrictionService;

	private volatile Snapshot snapshot;

	@Override
	public String productCatalogCsv()
	{
		return current().csv;
	}

	@Override
	public Map<String, String> categoryPaths()
	{
		return current().categoryPaths;
	}

	@Override
	public void clearCache()
	{
		snapshot = null;
	}

	protected Snapshot current()
	{
		final long ttl = Math.max(0, configurationService.getConfiguration().getLong("flamengo.emarsys.catalogo.cache.minutos", 60)) * 60_000L;
		Snapshot s = snapshot;
		if (s == null || System.currentTimeMillis() - s.createdAt > ttl)
		{
			synchronized (this)
			{
				s = snapshot;
				if (s == null || System.currentTimeMillis() - s.createdAt > ttl)
				{
					s = sessionService.executeInLocalView(new SessionExecutionBody()
					{
						@Override
						public Object execute()
						{
							searchRestrictionService.disableSearchRestrictions();
							return build();
						}
					}, userService.getAdminUser());
					snapshot = s;
				}
			}
		}
		return s;
	}

	protected Snapshot build()
	{
		final long start = System.currentTimeMillis();
		final CatalogVersionModel cv = catalogVersionService.getCatalogVersion(
				configurationService.getConfiguration().getString("flamengo.emarsys.catalogo.id", "flamengoProductCatalog"),
				configurationService.getConfiguration().getString("flamengo.emarsys.catalogo.versao", "Online"));
		final String store = StringUtils.removeEnd(
				configurationService.getConfiguration().getString("flamengo.emarsys.loja.url", "https://spartacus-flamengo.democc.online/electronics-spa"), "/");
		final String media = StringUtils.removeEnd(
				configurationService.getConfiguration().getString("flamengo.emarsys.midia.url", "https://api-flamengo.democc.online"), "/");
		final int maxDescription = configurationService.getConfiguration().getInt("flamengo.emarsys.descricao.max", 1000);
		final Map<String, Boolean> stock = stockByProductCode(
				configurationService.getConfiguration().getString("flamengo.emarsys.deposito", "warehouse_s"));

		final FlexibleSearchQuery query = new FlexibleSearchQuery(
				"SELECT {pk} FROM {Product} WHERE {catalogVersion} = ?cv ORDER BY {code}");
		query.addQueryParameter("cv", cv);
		final List<ProductModel> products = flexibleSearchService.<ProductModel> search(query).getResult();

		final StringBuilder csv = new StringBuilder();
		csv.append(String.join(",", HEADER)).append('\n');
		final Map<String, String> categoryPaths = new TreeMap<>();
		int rows = 0;
		for (final ProductModel product : products)
		{
			if (product instanceof VariantProductModel)
			{
				continue;
			}
			try
			{
				csv.append(row(product, store, media, maxDescription, stock, categoryPaths)).append('\n');
				rows++;
			}
			catch (final RuntimeException e)
			{
				LOG.warn("Emarsys: produto " + product.getCode() + " ficou fora do catálogo: " + e.getMessage());
			}
		}
		LOG.info("Emarsys: catálogo gerado com " + rows + " produtos em " + (System.currentTimeMillis() - start) + " ms");
		return new Snapshot(csv.toString(), Collections.unmodifiableMap(categoryPaths));
	}

	protected String row(final ProductModel product, final String store, final String media, final int maxDescription,
			final Map<String, Boolean> stock, final Map<String, String> categoryPaths)
	{
		final String code = product.getCode();
		final String type = type(code);
		final List<ProductModel> leaves = leaves(product);

		Double price = null;
		final List<ProductModel> priced = new ArrayList<>();
		priced.add(product);
		priced.addAll(leaves);
		for (final ProductModel p : priced)
		{
			final Double value = listPrice(p);
			if (value != null && (price == null || value < price))
			{
				price = value;
			}
		}
		boolean inStock = false;
		for (final ProductModel leaf : leaves)
		{
			if (Boolean.TRUE.equals(stock.get(leaf.getCode())))
			{
				inStock = true;
				break;
			}
		}
		final boolean available = !SERVICE_TYPES.contains(type) && price != null
				&& ("socio_torcedor".equals(type) || inStock);

		final Set<String> pathsPt = new LinkedHashSet<>();
		final Set<String> pathsEn = new LinkedHashSet<>();
		for (final CategoryModel category : categories(product.getSupercategories()))
		{
			final List<CategoryModel> path = path(category);
			final String pt = pathName(path, PT);
			pathsPt.add(pt);
			pathsEn.add(pathName(path, EN));
			for (int i = 0; i < path.size(); i++)
			{
				categoryPaths.put(path.get(i).getCode(), pathName(path.subList(0, i + 1), PT));
			}
		}

		final String route = "ingresso".equals(type) ? "/matchday?jogo=" + encode(code) : "/product/" + encode(code);
		final List<String> values = Arrays.asList(code, clean(product.getName(PT)), clean(product.getName(EN)),
				store + "/pt/BRL" + route, store + "/en/BRL" + route, image(product, media), String.join("|", pathsPt),
				String.join("|", pathsEn), price == null ? "" : String.format(Locale.ROOT, "%.2f", price),
				available ? "true" : "false", cut(clean(product.getDescription(PT)), maxDescription),
				cut(clean(product.getDescription(EN)), maxDescription), type);
		final StringBuilder line = new StringBuilder();
		for (int i = 0; i < values.size(); i++)
		{
			if (i > 0)
			{
				line.append(',');
			}
			line.append(quote(values.get(i)));
		}
		return line.toString();
	}

	/** Tipo do produto pelo prefixo do código (também vai para o campo próprio {@code tipo}). */
	protected String type(final String code)
	{
		if (code.startsWith("JOGO-"))
		{
			return "ingresso";
		}
		if (code.startsWith("EXP-"))
		{
			return "experiencia";
		}
		if (code.startsWith("NACAO-"))
		{
			return "socio_torcedor";
		}
		if (code.startsWith("FLA-EST-"))
		{
			return "estacionamento";
		}
		if (code.startsWith("FLA-FOOD-"))
		{
			return "alimentacao";
		}
		if (code.startsWith("FLA-PERS"))
		{
			return "personalizacao";
		}
		return "produto";
	}

	/** Variantes finais (jogo › setor › poltrona); sem variantes, o próprio produto. */
	protected List<ProductModel> leaves(final ProductModel product)
	{
		final List<ProductModel> result = new ArrayList<>();
		collectLeaves(product, result, 0);
		return result;
	}

	private void collectLeaves(final ProductModel product, final List<ProductModel> result, final int depth)
	{
		final Collection<VariantProductModel> variants = product.getVariants();
		if (variants == null || variants.isEmpty() || depth > 4)
		{
			result.add(product);
			return;
		}
		for (final VariantProductModel variant : variants)
		{
			collectLeaves(variant, result, depth + 1);
		}
	}

	protected Double listPrice(final ProductModel product)
	{
		Double best = null;
		final Collection<PriceRowModel> rows = product.getEurope1Prices();
		if (rows == null)
		{
			return null;
		}
		for (final PriceRowModel row : rows)
		{
			final boolean brl = row.getCurrency() != null && "BRL".equals(row.getCurrency().getIsocode());
			final boolean list = row.getUg() == null && row.getUser() == null
					&& (row.getMinqtd() == null || row.getMinqtd() <= 1);
			if (brl && list && row.getPrice() != null && (best == null || row.getPrice() < best))
			{
				best = row.getPrice();
			}
		}
		return best;
	}

	/** Produto com estoque no depósito, pelo código (disponível - reservado > 0, ou forçado em estoque). */
	protected Map<String, Boolean> stockByProductCode(final String warehouse)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery("SELECT {s.pk} FROM {StockLevel AS s JOIN Warehouse AS w "
				+ "ON {s.warehouse} = {w.pk}} WHERE {w.code} = ?warehouse");
		query.addQueryParameter("warehouse", warehouse);
		final Map<String, Boolean> result = new HashMap<>();
		for (final StockLevelModel level : flexibleSearchService.<StockLevelModel> search(query).getResult())
		{
			final InStockStatus status = level.getInStockStatus();
			final boolean ok;
			if (InStockStatus.FORCEINSTOCK.equals(status))
			{
				ok = true;
			}
			else if (InStockStatus.FORCEOUTOFSTOCK.equals(status))
			{
				ok = false;
			}
			else
			{
				ok = level.getAvailable() - level.getReserved() > 0;
			}
			result.merge(level.getProductCode(), ok, Boolean::logicalOr);
		}
		return result;
	}

	/** Categorias de navegação (sem classificação e sem categorias de variante). */
	protected List<CategoryModel> categories(final Collection<CategoryModel> categories)
	{
		final List<CategoryModel> result = new ArrayList<>();
		if (categories != null)
		{
			for (final CategoryModel category : categories)
			{
				if (!(category instanceof ClassificationClassModel) && !(category instanceof VariantCategoryModel)
						&& !(category instanceof VariantValueCategoryModel))
				{
					result.add(category);
				}
			}
		}
		return result;
	}

	/** Caminho da raiz até a categoria (pela primeira supercategoria de navegação). */
	protected List<CategoryModel> path(final CategoryModel category)
	{
		final List<CategoryModel> path = new ArrayList<>();
		CategoryModel current = category;
		while (current != null && !path.contains(current) && path.size() < 10)
		{
			path.add(0, current);
			final List<CategoryModel> parents = categories(current.getSupercategories());
			current = parents.isEmpty() ? null : parents.get(0);
		}
		return path;
	}

	protected String pathName(final List<CategoryModel> path, final Locale locale)
	{
		final List<String> names = new ArrayList<>();
		for (final CategoryModel category : path)
		{
			String name = category.getName(locale);
			if (StringUtils.isBlank(name))
			{
				name = category.getName(PT);
			}
			// ">" e "|" são separadores do Emarsys
			names.add(clean(StringUtils.defaultIfBlank(name, category.getCode())).replace('>', '-').replace('|', '/'));
		}
		return String.join(" > ", names);
	}

	protected String image(final ProductModel product, final String mediaBase)
	{
		MediaModel best = null;
		int bestWidth = -1;
		final List<MediaContainerModel> gallery = product.getGalleryImages();
		if (gallery != null && !gallery.isEmpty() && gallery.get(0).getMedias() != null)
		{
			for (final MediaModel m : gallery.get(0).getMedias())
			{
				final int width = width(m);
				if (acceptable(m) && width > bestWidth)
				{
					best = m;
					bestWidth = width;
				}
			}
		}
		if (best == null && acceptable(product.getPicture()))
		{
			best = product.getPicture();
		}
		return best == null || StringUtils.isBlank(best.getURL()) ? "" : mediaBase + best.getURL();
	}

	private boolean acceptable(final MediaModel media)
	{
		if (media == null || media.getMime() == null)
		{
			return false;
		}
		final String mime = media.getMime().toLowerCase(Locale.ROOT);
		final boolean format = mime.equals("image/jpeg") || mime.equals("image/png") || mime.equals("image/gif");
		return format && (media.getSize() == null || media.getSize() <= MAX_IMAGE_BYTES);
	}

	private int width(final MediaModel media)
	{
		if (media.getMediaFormat() == null || media.getMediaFormat().getQualifier() == null)
		{
			return 0;
		}
		final Matcher m = FORMAT_WIDTH.matcher(media.getMediaFormat().getQualifier());
		return m.find() ? Integer.parseInt(m.group(1)) : 0;
	}

	/** Texto puro numa linha: sem HTML e sem quebras (o Emarsys não aceita quebra de linha no campo). */
	protected String clean(final String value)
	{
		if (StringUtils.isBlank(value))
		{
			return "";
		}
		String s = TAGS.matcher(value).replaceAll(" ");
		s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">");
		return SPACES.matcher(s).replaceAll(" ").trim();
	}

	protected String cut(final String value, final int max)
	{
		if (max <= 0 || value.length() <= max)
		{
			return value;
		}
		final String head = value.substring(0, max - 1);
		final int space = head.lastIndexOf(' ');
		return (space > max / 2 ? head.substring(0, space) : head) + "…";
	}

	/** Aspas só quando precisa (vírgula, aspas ou barra vertical); aspas internas dobradas. */
	protected String quote(final String value)
	{
		final String s = value == null ? "" : value;
		if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('|') >= 0)
		{
			return '"' + s.replace("\"", "\"\"") + '"';
		}
		return s;
	}

	private String encode(final String value)
	{
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}


	protected static final class Snapshot
	{
		final String csv;
		final Map<String, String> categoryPaths;
		final long createdAt = System.currentTimeMillis();

		Snapshot(final String csv, final Map<String, String> categoryPaths)
		{
			this.csv = csv;
			this.categoryPaths = categoryPaths;
		}
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	public void setCatalogVersionService(final CatalogVersionService catalogVersionService)
	{
		this.catalogVersionService = catalogVersionService;
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

	public void setSearchRestrictionService(final SearchRestrictionService searchRestrictionService)
	{
		this.searchRestrictionService = searchRestrictionService;
	}
}
