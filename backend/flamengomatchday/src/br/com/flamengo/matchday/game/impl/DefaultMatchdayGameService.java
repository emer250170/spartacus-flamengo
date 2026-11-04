package br.com.flamengo.matchday.game.impl;

import br.com.flamengo.matchday.util.BackofficeRequestContext;
import br.com.flamengo.matchday.enums.MatchdayGameStatus;
import br.com.flamengo.matchday.game.MatchdayGameService;
import br.com.flamengo.matchday.model.MatchdayGameModel;
import br.com.flamengo.matchday.model.SeatHoldModel;
import br.com.flamengo.matchday.service.SeatHoldRemovalContext;
import de.hybris.platform.catalog.CatalogVersionService;
import de.hybris.platform.catalog.enums.ArticleApprovalStatus;
import de.hybris.platform.catalog.model.CatalogVersionModel;
import de.hybris.platform.category.model.CategoryModel;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.cronjob.model.CronJobModel;
import de.hybris.platform.europe1.model.PriceRowModel;
import de.hybris.platform.impex.model.ImpExMediaModel;
import de.hybris.platform.ordersplitting.model.StockLevelModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.cronjob.CronJobService;
import de.hybris.platform.servicelayer.impex.ImportConfig;
import de.hybris.platform.servicelayer.impex.ImportResult;
import de.hybris.platform.servicelayer.impex.ImportService;
import de.hybris.platform.servicelayer.impex.impl.StreamBasedImpExResource;
import de.hybris.platform.servicelayer.media.MediaService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.variants.model.VariantProductModel;
import br.com.flamengo.matchday.media.ProductImageService;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DefaultMatchdayGameService implements MatchdayGameService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultMatchdayGameService.class);

	private static final TimeZone TZ = TimeZone.getTimeZone("America/Sao_Paulo");
	private static final Locale PT = new Locale("pt");
	private static final Locale EN = new Locale("en");
	private static final String[] MONTHS_PT = { "JAN", "FEV", "MAR", "ABR", "MAI", "JUN", "JUL", "AGO", "SET", "OUT", "NOV",
			"DEZ" };
	private static final String[] MONTHS_EN = { "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV",
			"DEC" };
	private static final String DOT = " · ";

	private ModelService modelService;
	private FlexibleSearchService flexibleSearchService;
	private CatalogVersionService catalogVersionService;
	private ProductImageService productImageService;
	private ImportService importService;
	private MediaService mediaService;
	private CronJobService cronJobService;
	private ConfigurationService configurationService;

	// ---------------------------------------------------------------- template

	@Override
	public Map<String, Double> getTemplatePrices()
	{
		final String template = template();
		final FlexibleSearchQuery query = new FlexibleSearchQuery(
				"SELECT {pk} FROM {PriceRow} WHERE {productId} LIKE ?p ORDER BY {productId}");
		query.addQueryParameter("p", template + "-%");
		final Map<String, Double> prices = new LinkedHashMap<>();
		for (final PriceRowModel row : flexibleSearchService.<PriceRowModel> search(query).getResult())
		{
			final String sector = row.getProductId().substring(template.length() + 1).split("-")[0];
			prices.putIfAbsent(sector, row.getPrice());
		}
		return prices;
	}

	@Override
	public String generateCode(final MatchdayGameModel game)
	{
		final SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
		format.setTimeZone(TZ);
		final String date = game.getKickoff() == null ? "00000000" : format.format(game.getKickoff());
		final String letters = Normalizer.normalize(StringUtils.defaultString(game.getOpponent()), Normalizer.Form.NFD)
				.replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT);
		final String acronym = letters.isEmpty() ? "ADV" : letters.substring(0, Math.min(3, letters.length()));
		return "JOGO-" + date + "-" + acronym;
	}

	// ---------------------------------------------------------------- abrir / atualizar

	@Override
	public String open(final MatchdayGameModel game)
	{
		final CatalogVersionModel online = online();
		final ProductModel existing = findProduct(game.getCode(), online);
		if (existing != null && isUsedByAnotherGame(existing, game))
		{
			throw new IllegalStateException("Já existe outro jogo com o código " + game.getCode()
					+ ". Mude a data ou o adversário.");
		}
		final int[] result = importGame(game, true);
		final ProductModel product = findProduct(game.getCode(), online);
		if (product == null)
		{
			throw new IllegalStateException("O produto " + game.getCode() + " não foi criado.");
		}
		game.setProduct(product);
		final String image = copyImage(game, product);
		reindex();
		return "Jogo aberto: " + result[0] + " poltronas, " + result[1] + " à venda." + (image == null ? "" : " " + image);
	}

	@Override
	public String update(final MatchdayGameModel game)
	{
		importGame(game, false);
		final String image = copyImage(game, game.getProduct());
		reindex();
		return "Nome, descrição e preços atualizados na loja." + (image == null ? "" : " " + image);
	}

	/** Imagem do jogo no produto (as poltronas usam a do produto do jogo); erro na imagem não impede salvar o jogo */
	protected String copyImage(final MatchdayGameModel game, final ProductModel product)
	{
		if (productImageService == null || product == null)
		{
			return null;
		}
		try
		{
			return productImageService.copyToProduct(game.getImage(), product);
		}
		catch (final RuntimeException e)
		{
			LOG.warn("Imagem do jogo {} não copiada: {}", game.getCode(), e.getMessage(), e);
			return "A imagem não foi copiada para o produto (" + StringUtils.defaultString(e.getMessage()) + ").";
		}
	}

	@Override
	public String setOnSale(final MatchdayGameModel game, final boolean onSale)
	{
		final ProductModel product = game.getProduct();
		if (product == null)
		{
			return null;
		}
		product.setApprovalStatus(onSale ? ArticleApprovalStatus.APPROVED : ArticleApprovalStatus.UNAPPROVED);
		final List<CategoryModel> categories = new ArrayList<>(
				product.getSupercategories() == null ? Collections.emptyList() : product.getSupercategories());
		final boolean inStore = categories.stream().anyMatch(c -> storeCategory().equals(c.getCode()));
		if (onSale && !inStore)
		{
			final CategoryModel store = findCategory(storeCategory(), product.getCatalogVersion());
			if (store != null)
			{
				categories.add(0, store);
				product.setSupercategories(categories);
			}
		}
		modelService.save(product);
		reindex();
		return onSale ? "Venda retomada: o jogo voltou para a loja." : "Venda pausada: o jogo saiu da loja.";
	}

	/**
	 * Monta o ImpEx do jogo a partir do template e importa.
	 *
	 * @return [poltronas, poltronas a venda]
	 */
	protected int[] importGame(final MatchdayGameModel game, final boolean withStock)
	{
		final CatalogVersionModel staged = catalogVersionService.getCatalogVersion(catalog(), "Staged");
		final ProductModel template = findProduct(template(), staged);
		if (template == null)
		{
			throw new IllegalStateException("Template " + template() + " não encontrado (versão Staged).");
		}
		final List<VariantProductModel> seats = findVariants(template);
		if (seats.isEmpty())
		{
			throw new IllegalStateException("O template " + template() + " não tem poltronas.");
		}
		final Map<String, Double> templateSeatPrices = templateSeatPrices();
		final Set<String> stocked = withStock ? existingStockCodes(game.getCode()) : Collections.emptySet();
		final String code = game.getCode();
		final boolean onSale = game.getStatus() == MatchdayGameStatus.ON_SALE;

		final String title = "Flamengo x " + clean(game.getOpponent());
		final String namePt = title + DOT + datePt(game.getKickoff()) + DOT + hourPt(game.getKickoff());
		final String nameEn = title + DOT + dateEn(game.getKickoff()) + DOT + hourEn(game.getKickoff());
		final String venue = StringUtils.defaultIfBlank(clean(game.getVenue()), "Maracanã");
		final String competition = clean(game.getCompetition());
		final String summary = StringUtils.isBlank(competition) ? venue : competition + DOT + venue;
		final String description = title + ", " + datePt(game.getKickoff()) + ", " + hourPt(game.getKickoff()) + ", no "
				+ venue + "." + salesText(game)
				+ " Escolha setor e poltrona e monte o seu dia de jogo: estacionamento, comidas e bebidas e a camisa nova no mesmo pedido.";
		// loja em ingles: mesmos dados, texto em ingles ("Rodada 35" -> "Round 35")
		final String summaryEn = summary.replaceAll("\\bRodada\\b", "Round");
		final String descriptionEn = title + ", " + dateEn(game.getKickoff()) + ", " + hourEn(game.getKickoff()) + ", at "
				+ venue + "." + salesTextEn(game)
				+ " Pick your sector and seat and build your matchday: parking, food and drinks and the new jersey in the same order.";

		final List<String> variantLines = new ArrayList<>();
		final List<String> priceLines = new ArrayList<>();
		final List<String> stockLines = new ArrayList<>();
		final int percent = game.getSimulatedSoldPercent() == null ? 0 : game.getSimulatedSoldPercent().intValue();
		int forSale = 0;
		Double minPrice = null;
		for (final VariantProductModel seat : seats)
		{
			final String suffix = seat.getCode().substring(template().length() + 1);
			final String sector = suffix.split("-")[0];
			final String seatCode = code + "-" + suffix;
			final Double price = priceFor(game, sector, templateSeatPrices.get(seat.getCode()));
			minPrice = minPrice == null ? price : Math.min(minPrice, price);
			final String sectorName = categoryName(seat, "setor-", PT);
			final String sectorNameEn = categoryName(seat, "setor-", EN);
			final String seatName = categoryName(seat, "poltrona-", PT);
			final String categories = seat.getSupercategories().stream().map(CategoryModel::getCode)
					.collect(Collectors.joining(","));
			variantLines.add(";" + seatCode + ";;" + code + ";" + q(title + DOT + sectorName + DOT + seatName) + ";"
					+ q(title + DOT + sectorNameEn + DOT + seatName) + ";" + categories + ";;approved");
			priceLines.add(";" + seatCode + ";;" + currency() + ";" + amount(price) + ";1;1;false");
			final boolean sold = Math.abs((code + suffix).hashCode()) % 100 < percent;
			if (!sold)
			{
				forSale++;
			}
			if (withStock && !stocked.contains(seatCode))
			{
				stockLines.add(";" + seatCode + ";;" + (sold ? 0 : 1) + ";0;0;0;0;notSpecified");
			}
		}

		final List<String> lines = new ArrayList<>();
		lines.add("$productCatalog=" + catalog());
		lines.add("$catalogVersion=catalogversion(catalog(id[default=$productCatalog]),version[default='Online'])"
				+ "[unique=true,default=$productCatalog:Online]");
		lines.add("$supercategories=supercategories(code,$catalogVersion)");
		lines.add("$baseProduct=baseProduct(code,$catalogVersion)");
		lines.add("INSERT_UPDATE Product;code[unique=true];$catalogVersion;name[lang=pt];name[lang=en];summary[lang=pt];"
				+ "summary[lang=en];description[lang=pt];description[lang=en];$supercategories;variantType(code);unit(code)[default=pieces];"
				+ "approvalstatus(code)");
		lines.add(";" + code + ";;" + q(namePt) + ";" + q(nameEn) + ";" + q(summary) + ";" + q(summaryEn) + ";" + q(description)
				+ ";" + q(descriptionEn) + ";" + (onSale ? storeCategory() + "," : "") + "setor,poltrona;GenericVariantProduct;;"
				+ (onSale ? "approved" : "unapproved"));
		lines.add("INSERT_UPDATE GenericVariantProduct;code[unique=true];$catalogVersion;$baseProduct;name[lang=pt];"
				+ "name[lang=en];$supercategories;unit(code)[default=pieces];approvalstatus(code)");
		lines.addAll(variantLines);
		lines.add("INSERT_UPDATE PriceRow;productId[unique=true];unit(code[unique=true,default=pieces]);"
				+ "currency(isocode)[unique=true];price;minqtd;unitFactor;net");
		lines.add(";" + code + ";;" + currency() + ";" + amount(minPrice) + ";1;1;false");
		lines.addAll(priceLines);
		if (withStock && (!stockLines.isEmpty() || !stocked.contains(code)))
		{
			lines.add("INSERT_UPDATE StockLevel;productCode[unique=true];warehouse(code)[unique=true,default=" + warehouse()
					+ "];available;reserved;preOrder;maxPreOrder;maxStockLevelHistoryCount;inStockStatus(code)");
			if (!stocked.contains(code))
			{
				// so para o card do jogo nao aparecer esgotado
				lines.add(";" + code + ";;" + forSale + ";0;0;0;0;notSpecified");
			}
			lines.addAll(stockLines);
		}
		runImpex(lines, code);
		LOG.info("Jogo {} importado ({} poltronas, {} novas no estoque)", code, seats.size(), stockLines.size());
		return new int[] { seats.size(), forSale };
	}

	protected Double priceFor(final MatchdayGameModel game, final String sector, final Double templatePrice)
	{
		final Double price;
		switch (sector)
		{
			case SECTOR_NORTE:
				price = game.getPriceNorte();
				break;
			case SECTOR_SUL:
				price = game.getPriceSul();
				break;
			case SECTOR_LESTE:
				price = game.getPriceLeste();
				break;
			case SECTOR_OESTE:
				price = game.getPriceOeste();
				break;
			default:
				price = null;
		}
		if (price != null && price.doubleValue() > 0)
		{
			return price;
		}
		return templatePrice == null ? Double.valueOf(0) : templatePrice;
	}

	protected void runImpex(final List<String> lines, final String code)
	{
		final ImportConfig config = new ImportConfig();
		config.setScript(new StreamBasedImpExResource(
				new ByteArrayInputStream(String.join("\n", lines).getBytes(StandardCharsets.UTF_8)), "UTF-8"));
		config.setSynchronous(true);
		config.setLegacyMode(Boolean.FALSE);
		// o ImpEx abre e fecha uma sessão nesta thread; no Backoffice isso apaga o contexto da tela (ver BackofficeRequestContext)
		final ImportResult result;
		final Runnable restoreBackofficeContext = BackofficeRequestContext.capture();
		try
		{
			result = importService.importData(config);
		}
		finally
		{
			restoreBackofficeContext.run();
		}
		if (!result.isSuccessful())
		{
			String detail = "";
			final ImpExMediaModel unresolved = result.getUnresolvedLines();
			if (unresolved != null)
			{
				detail = new String(mediaService.getDataFromMedia(unresolved), StandardCharsets.UTF_8);
			}
			LOG.error("Falha no ImpEx do jogo {}:\n{}", code, detail);
			throw new IllegalStateException("Não foi possível gravar o jogo " + code + ". "
					+ StringUtils.abbreviate(detail.replaceAll("\\s+", " "), 400));
		}
	}

	// ---------------------------------------------------------------- encerrar

	@Override
	public String close(final MatchdayGameModel game)
	{
		final List<String> report = new ArrayList<>();
		for (final String version : Arrays.asList("Online", "Staged"))
		{
			final CatalogVersionModel catalogVersion;
			try
			{
				catalogVersion = catalogVersionService.getCatalogVersion(catalog(), version);
			}
			catch (final RuntimeException e)
			{
				continue;
			}
			final ProductModel base = findProduct(game.getCode(), catalogVersion);
			if (base == null)
			{
				continue;
			}
			base.setApprovalStatus(ArticleApprovalStatus.UNAPPROVED);
			base.setSupercategories(base.getSupercategories().stream().filter(c -> !storeCategory().equals(c.getCode()))
					.collect(Collectors.toList()));
			modelService.save(base);

			int sold = 0;
			int removed = 0;
			int carts = 0;
			for (final VariantProductModel seat : findVariants(base))
			{
				if (hasOrders(seat))
				{
					sold++;
					seat.setApprovalStatus(ArticleApprovalStatus.UNAPPROVED);
					modelService.save(seat);
				}
				else
				{
					carts += removeFromCarts(seat);
					removeHolds(seat);
					removeProductData(seat.getCode());
					modelService.remove(seat);
					removed++;
				}
			}
			boolean baseRemoved = false;
			if (sold == 0 && !hasOrders(base))
			{
				removeFromCarts(base);
				removeProductData(base.getCode());
				if (base.equals(game.getProduct()))
				{
					game.setProduct(null);
				}
				modelService.remove(base);
				baseRemoved = true;
			}
			report.add(version + ": " + sold + " poltronas vendidas mantidas, " + removed + " não vendidas apagadas"
					+ (carts > 0 ? ", " + carts + " itens tirados de carrinhos" : "")
					+ (baseRemoved ? ", produto do jogo apagado" : ", produto do jogo fora da loja"));
		}
		reindex();
		return report.isEmpty() ? "Jogo encerrado (não havia produto na loja)."
				: "Jogo encerrado. " + String.join("; ", report) + ".";
	}

	protected boolean hasOrders(final ProductModel product)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery("SELECT {pk} FROM {OrderEntry} WHERE {product}=?p");
		query.addQueryParameter("p", product);
		query.setCount(1);
		return !flexibleSearchService.search(query).getResult().isEmpty();
	}

	protected int removeFromCarts(final ProductModel product)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery("SELECT {pk} FROM {CartEntry} WHERE {product}=?p");
		query.addQueryParameter("p", product);
		final List<AbstractOrderEntryModel> entries = new ArrayList<>(
				flexibleSearchService.<AbstractOrderEntryModel> search(query).getResult());
		for (final AbstractOrderEntryModel entry : entries)
		{
			final AbstractOrderModel cart = entry.getOrder();
			modelService.remove(entry);
			if (cart != null)
			{
				modelService.refresh(cart);
				cart.setCalculated(Boolean.FALSE);
				modelService.save(cart);
			}
		}
		return entries.size();
	}

	protected void removeHolds(final ProductModel seat)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery(
				"SELECT {pk} FROM {" + SeatHoldModel._TYPECODE + "} WHERE {" + SeatHoldModel.PRODUCT + "}=?p");
		query.addQueryParameter("p", seat);
		// remocao do proprio jogo encerrado: o carrinho ja e tratado aqui
		SeatHoldRemovalContext.runInternal(() -> modelService.removeAll(flexibleSearchService.search(query).getResult()));
	}

	protected void removeProductData(final String productCode)
	{
		final FlexibleSearchQuery prices = new FlexibleSearchQuery("SELECT {pk} FROM {PriceRow} WHERE {productId}=?c");
		prices.addQueryParameter("c", productCode);
		modelService.removeAll(flexibleSearchService.search(prices).getResult());
		final FlexibleSearchQuery stock = new FlexibleSearchQuery("SELECT {pk} FROM {StockLevel} WHERE {productCode}=?c");
		stock.addQueryParameter("c", productCode);
		modelService.removeAll(flexibleSearchService.search(stock).getResult());
	}

	// ---------------------------------------------------------------- poltronas

	@Override
	public int[] countSeats(final String gameCode)
	{
		if (StringUtils.isBlank(gameCode))
		{
			return new int[] { 0, 0, 0 };
		}
		final FlexibleSearchQuery query = new FlexibleSearchQuery("SELECT {pk} FROM {StockLevel} WHERE {productCode} LIKE ?p");
		query.addQueryParameter("p", gameCode + "-%");
		int total = 0;
		int free = 0;
		int sold = 0;
		for (final StockLevelModel level : flexibleSearchService.<StockLevelModel> search(query).getResult())
		{
			total++;
			if (level.getAvailable() <= 0)
			{
				sold++;
			}
			else if (level.getAvailable() - level.getReserved() > 0)
			{
				free++;
			}
		}
		return new int[] { total, free, sold };
	}

	// ---------------------------------------------------------------- apoio

	protected ProductModel findProduct(final String code, final CatalogVersionModel catalogVersion)
	{
		if (StringUtils.isBlank(code))
		{
			return null;
		}
		final FlexibleSearchQuery query = new FlexibleSearchQuery(
				"SELECT {pk} FROM {Product} WHERE {code}=?c AND {catalogVersion}=?cv");
		query.addQueryParameter("c", code);
		query.addQueryParameter("cv", catalogVersion);
		final List<ProductModel> result = flexibleSearchService.<ProductModel> search(query).getResult();
		return result.isEmpty() ? null : result.get(0);
	}

	protected CategoryModel findCategory(final String code, final CatalogVersionModel catalogVersion)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery(
				"SELECT {pk} FROM {Category} WHERE {code}=?c AND {catalogVersion}=?cv");
		query.addQueryParameter("c", code);
		query.addQueryParameter("cv", catalogVersion);
		final List<CategoryModel> result = flexibleSearchService.<CategoryModel> search(query).getResult();
		return result.isEmpty() ? null : result.get(0);
	}

	protected List<VariantProductModel> findVariants(final ProductModel base)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery(
				"SELECT {pk} FROM {VariantProduct} WHERE {baseProduct}=?b ORDER BY {code}");
		query.addQueryParameter("b", base);
		return new ArrayList<>(flexibleSearchService.<VariantProductModel> search(query).getResult());
	}

	protected boolean isUsedByAnotherGame(final ProductModel product, final MatchdayGameModel game)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery(
				"SELECT {pk} FROM {" + MatchdayGameModel._TYPECODE + "} WHERE {" + MatchdayGameModel.PRODUCT + "}=?p");
		query.addQueryParameter("p", product);
		final List<MatchdayGameModel> games = flexibleSearchService.<MatchdayGameModel> search(query).getResult();
		return games.stream().anyMatch(g -> game.getPk() == null || !g.getPk().equals(game.getPk()));
	}

	protected Map<String, Double> templateSeatPrices()
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery("SELECT {pk} FROM {PriceRow} WHERE {productId} LIKE ?p");
		query.addQueryParameter("p", template() + "-%");
		final Map<String, Double> prices = new LinkedHashMap<>();
		for (final PriceRowModel row : flexibleSearchService.<PriceRowModel> search(query).getResult())
		{
			prices.put(row.getProductId(), row.getPrice());
		}
		return prices;
	}

	protected Set<String> existingStockCodes(final String code)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery(
				"SELECT {pk} FROM {StockLevel} WHERE {productCode} = ?c OR {productCode} LIKE ?p");
		query.addQueryParameter("c", code);
		query.addQueryParameter("p", code + "-%");
		final Set<String> codes = new HashSet<>();
		for (final StockLevelModel level : flexibleSearchService.<StockLevelModel> search(query).getResult())
		{
			codes.add(level.getProductCode());
		}
		return codes;
	}

	protected String categoryName(final ProductModel seat, final String prefix, final Locale locale)
	{
		final Collection<CategoryModel> categories = seat.getSupercategories();
		if (categories != null)
		{
			for (final CategoryModel category : categories)
			{
				if (category.getCode() != null && category.getCode().startsWith(prefix))
				{
					return StringUtils.defaultIfBlank(category.getName(locale),
							StringUtils.defaultString(category.getName(PT), category.getCode()));
				}
			}
		}
		return "";
	}

	protected void reindex()
	{
		final String code = configurationService.getConfiguration().getString("flamengomatchday.solr.cronjob",
				"full-electronicsIndex-cronJob");
		try
		{
			final CronJobModel cronJob = cronJobService.getCronJob(code);
			if (!cronJobService.isRunning(cronJob))
			{
				cronJobService.performCronJob(cronJob, false);
			}
		}
		catch (final RuntimeException e)
		{
			LOG.warn("Não foi possível disparar a reindexação {}: {}", code, e.getMessage());
		}
	}

	/**
	 * Texto das ondas de venda na descrição do produto. A loja lê as datas daqui
	 * (formato fixo "Onda N (...): dd/MM/aaaa HHhmm"; horário de Brasília).
	 */
	protected static String salesText(final MatchdayGameModel game)
	{
		if (game.getWave1Start() == null || game.getWave2Start() == null || game.getWave3Start() == null
				|| game.getGeneralSaleStart() == null)
		{
			return Boolean.TRUE.equals(game.getPresale()) ? " Pré-venda para sócios." : "";
		}
		return " Pré-venda para sócios: Onda 1 (Nação Maraca 1): " + waveDate(game.getWave1Start())
				+ "; Onda 2 (Nação Maraca 2): " + waveDate(game.getWave2Start()) + "; Onda 3 (Nação Maraca 3): "
				+ waveDate(game.getWave3Start()) + "; Venda geral: " + waveDate(game.getGeneralSaleStart()) + ".";
	}

	/**
	 * O mesmo texto das ondas para a loja em ingles: "Wave N (...): aaaa-MM-dd HH:mm" (horario de Brasilia).
	 */
	protected static String salesTextEn(final MatchdayGameModel game)
	{
		if (game.getWave1Start() == null || game.getWave2Start() == null || game.getWave3Start() == null
				|| game.getGeneralSaleStart() == null)
		{
			return Boolean.TRUE.equals(game.getPresale()) ? " Member presale." : "";
		}
		return " Member presale: Wave 1 (Nação Maraca 1): " + waveDateEn(game.getWave1Start())
				+ "; Wave 2 (Nação Maraca 2): " + waveDateEn(game.getWave2Start()) + "; Wave 3 (Nação Maraca 3): "
				+ waveDateEn(game.getWave3Start()) + "; General sale: " + waveDateEn(game.getGeneralSaleStart())
				+ " (Brasília time).";
	}

	protected static String waveDateEn(final Date date)
	{
		final Calendar c = calendar(date);
		return String.format("%04d-%02d-%02d %02d:%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1,
				c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
	}

	protected static String waveDate(final Date date)
	{
		final Calendar c = calendar(date);
		return String.format("%02d/%02d/%04d %02dh%02d", c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1,
				c.get(Calendar.YEAR), c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
	}

	protected static String datePt(final Date date)
	{
		final Calendar c = calendar(date);
		return c == null ? "" : c.get(Calendar.DAY_OF_MONTH) + " " + MONTHS_PT[c.get(Calendar.MONTH)];
	}

	protected static String dateEn(final Date date)
	{
		final Calendar c = calendar(date);
		return c == null ? "" : c.get(Calendar.DAY_OF_MONTH) + " " + MONTHS_EN[c.get(Calendar.MONTH)];
	}

	protected static String hourPt(final Date date)
	{
		final Calendar c = calendar(date);
		return c == null ? "" : String.format("%dh%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
	}

	protected static String hourEn(final Date date)
	{
		final Calendar c = calendar(date);
		if (c == null)
		{
			return "";
		}
		final int hour = c.get(Calendar.HOUR_OF_DAY);
		final int minute = c.get(Calendar.MINUTE);
		final int h12 = hour % 12 == 0 ? 12 : hour % 12;
		return h12 + (minute > 0 ? String.format(":%02d", minute) : "") + (hour < 12 ? "am" : "pm");
	}

	protected static Calendar calendar(final Date date)
	{
		if (date == null)
		{
			return null;
		}
		final Calendar c = Calendar.getInstance(TZ);
		c.setTime(date);
		return c;
	}

	/** Texto entre aspas para o ImpEx (aspas internas dobradas) */
	protected static String q(final String value)
	{
		return "\"" + StringUtils.defaultString(value).replace("\"", "\"\"") + "\"";
	}

	protected static String clean(final String value)
	{
		return StringUtils.trimToEmpty(value).replaceAll("[\\r\\n]+", " ");
	}

	protected static String amount(final Double value)
	{
		return value == null ? "0" : BigDecimal.valueOf(value.doubleValue()).stripTrailingZeros().toPlainString();
	}

	protected CatalogVersionModel online()
	{
		return catalogVersionService.getCatalogVersion(catalog(), "Online");
	}

	protected String catalog()
	{
		return configurationService.getConfiguration().getString("flamengomatchday.catalog", "flamengoProductCatalog");
	}

	protected String template()
	{
		return configurationService.getConfiguration().getString("flamengomatchday.template", "FLA-TEMPLATE-MARACANA");
	}

	protected String storeCategory()
	{
		return configurationService.getConfiguration().getString("flamengomatchday.storecategory", "flamengo_jogos_casa");
	}

	protected String currency()
	{
		return configurationService.getConfiguration().getString("flamengomatchday.currency", "BRL");
	}

	protected String warehouse()
	{
		return configurationService.getConfiguration().getString("flamengomatchday.warehouse", "warehouse_s");
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	public void setProductImageService(final ProductImageService productImageService)
	{
		this.productImageService = productImageService;
	}

	public void setCatalogVersionService(final CatalogVersionService catalogVersionService)
	{
		this.catalogVersionService = catalogVersionService;
	}

	public void setImportService(final ImportService importService)
	{
		this.importService = importService;
	}

	public void setMediaService(final MediaService mediaService)
	{
		this.mediaService = mediaService;
	}

	public void setCronJobService(final CronJobService cronJobService)
	{
		this.cronJobService = cronJobService;
	}

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}
}
