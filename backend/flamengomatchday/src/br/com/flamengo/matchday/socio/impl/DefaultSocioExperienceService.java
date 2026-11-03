package br.com.flamengo.matchday.socio.impl;

import br.com.flamengo.matchday.enums.SocioEventType;
import br.com.flamengo.matchday.model.SocioEventModel;
import br.com.flamengo.matchday.model.SocioExperienceModel;
import br.com.flamengo.matchday.model.SocioRedemptionModel;
import br.com.flamengo.matchday.socio.SocioExperienceOverview;
import br.com.flamengo.matchday.socio.SocioExperienceService;
import br.com.flamengo.matchday.socio.SocioPlanService;
import br.com.flamengo.matchday.socio.SocioPointsException;
import br.com.flamengo.matchday.util.BackofficeRequestContext;
import de.hybris.platform.catalog.CatalogVersionService;
import de.hybris.platform.catalog.model.CatalogVersionModel;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.cronjob.model.CronJobModel;
import de.hybris.platform.impex.model.ImpExMediaModel;
import de.hybris.platform.ordersplitting.model.StockLevelModel;
import de.hybris.platform.search.restriction.SearchRestrictionService;
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
import de.hybris.platform.servicelayer.session.SessionExecutionBody;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.servicelayer.user.UserService;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DefaultSocioExperienceService implements SocioExperienceService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultSocioExperienceService.class);
	private static final Locale PT = new Locale("pt");
	private static final Locale EN = new Locale("en");
	private static final String CATALOG = "flamengoProductCatalog";
	private static final String WAREHOUSE = "warehouse_s";
	/** Estoque de "sem limite" (o mesmo do plano) */
	private static final int UNLIMITED = 100000;
	private static final String VOUCHER_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final SecureRandom RANDOM = new SecureRandom();

	private ConfigurationService configurationService;
	private FlexibleSearchService flexibleSearchService;
	private ModelService modelService;
	private CatalogVersionService catalogVersionService;
	private ImportService importService;
	private MediaService mediaService;
	private CronJobService cronJobService;
	private SessionService sessionService;
	private SearchRestrictionService searchRestrictionService;
	private UserService userService;
	private SocioPlanService socioPlanService;

	@Override
	public boolean isSaleEnabled()
	{
		return configurationService.getConfiguration().getBoolean(SALE_ENABLED_PROPERTY, false);
	}

	// ------------------------------------------------------------ produto

	@Override
	public String publish(final SocioExperienceModel experience)
	{
		// tudo como admin (como no plano): o gestor de experiências só precisa de acesso à experiência
		return sessionService.executeInLocalView(new SessionExecutionBody()
		{
			@Override
			public Object execute()
			{
				userService.setCurrentUser(userService.getAdminUser());
				return doPublish(experience);
			}
		});
	}

	protected String doPublish(final SocioExperienceModel experience)
	{
		final boolean active = !Boolean.FALSE.equals(experience.getActive());
		final boolean priced = experience.getPrice() != null && experience.getPrice().doubleValue() > 0;
		final boolean onSale = isSaleEnabled() && active && priced;
		if (!onSale && experience.getProduct() == null)
		{
			return priced && !isSaleEnabled() ? "Venda na loja desligada (flamengo.socio.experiencias.venda.ativo). Só resgate com pontos."
					: "Só resgate com pontos (sem preço na loja).";
		}
		final String code = PRODUCT_PREFIX + experience.getCode();
		final String namePt = StringUtils.defaultIfBlank(experience.getName(PT), experience.getCode());
		final String nameEn = StringUtils.defaultIfBlank(experience.getName(EN), namePt);
		final int points = experience.getPoints() == null ? 0 : experience.getPoints().intValue();
		final String summaryPt = "Experiência Nação · sócio também resgata com " + number(points) + " pontos";
		final String summaryEn = "Nação experience · members can also redeem it for " + number(points) + " points";
		final String descPt = StringUtils.defaultString(experience.getDescription(PT));
		final String descEn = StringUtils.defaultIfBlank(experience.getDescription(EN), descPt);
		final Integer remaining = remaining(experience, 0);

		final List<String> lines = new ArrayList<>();
		lines.add("$cv=catalogVersion(catalog(id[default=" + CATALOG + "]),version[default=Online])[unique=true,default=" + CATALOG
				+ ":Online]");
		lines.add("INSERT_UPDATE Category;code[unique=true];$cv;name[lang=pt];name[lang=en];allowedPrincipals(uid)[default='customergroup']");
		lines.add(";" + CATEGORY + ";;Experiências;Experiences;");
		lines.add("INSERT_UPDATE Product;code[unique=true];$cv;name[lang=pt];name[lang=en];summary[lang=pt];summary[lang=en];"
				+ "description[lang=pt];description[lang=en];supercategories(code,$cv);unit(code)[default=pieces];approvalstatus(code)");
		lines.add(";" + code + ";;" + q(namePt) + ";" + q(nameEn) + ";" + q(summaryPt) + ";" + q(summaryEn) + ";" + q(descPt) + ";"
				+ q(descEn) + ";" + CATEGORY + ";;" + (onSale ? "approved" : "unapproved"));
		if (priced)
		{
			lines.add("INSERT_UPDATE PriceRow;productId[unique=true];unit(code[unique=true,default=pieces]);currency(isocode)[unique=true];"
					+ "price;minqtd[default=1];unitFactor[default=1];net[default=false]");
			lines.add(";" + code + ";;BRL;" + experience.getPrice().doubleValue() + ";;;");
		}
		lines.add("INSERT_UPDATE StockLevel;productCode[unique=true];warehouse(code)[unique=true,default=" + WAREHOUSE
				+ "];available;reserved[default=0];preOrder[default=0];maxPreOrder[default=0];maxStockLevelHistoryCount[default=0];"
				+ "inStockStatus(code)[default=notSpecified]");
		lines.add(";" + code + ";;" + (remaining == null ? UNLIMITED : remaining.intValue()) + ";;;;;");
		runImpex(lines, code);
		final ProductModel product = findProduct(code);
		experience.setProduct(product);
		reindex();
		final String stock = remaining == null ? "sem limite de vagas" : number(remaining.intValue()) + " vagas";
		return onSale ? "Na loja: " + code + " por " + money(experience.getPrice().doubleValue()) + ", " + stock + "."
				: "Fora da loja (" + (!isSaleEnabled() ? "venda desligada" : !active ? "experiência inativa" : "sem preço")
						+ "). Quem já comprou continua com o voucher.";
	}

	@Override
	public Integer remaining(final SocioExperienceModel experience, final int delta)
	{
		if (experience == null || experience.getStock() == null)
		{
			return null;
		}
		final int used = experience.getPk() == null ? 0 : unrestricted(() -> {
			final FlexibleSearchQuery q = new FlexibleSearchQuery(
					"SELECT COUNT({pk}) FROM {SocioRedemption} WHERE {experience}=?e AND {cancelledAt} IS NULL");
			q.addQueryParameter("e", experience);
			q.setResultClassList(List.of(Integer.class));
			final List<Integer> result = flexibleSearchService.<Integer> search(q).getResult();
			return result.isEmpty() || result.get(0) == null ? Integer.valueOf(0) : result.get(0);
		}).intValue();
		return Integer.valueOf(Math.max(0, experience.getStock().intValue() - used + delta));
	}

	@Override
	public StockLevelModel adjustStock(final SocioExperienceModel experience, final int delta)
	{
		if (experience == null || experience.getProduct() == null)
		{
			return null;
		}
		final StockLevelModel level = unrestricted(() -> {
			final FlexibleSearchQuery q = new FlexibleSearchQuery("SELECT {s.pk} FROM {StockLevel AS s JOIN Warehouse AS w ON {s.warehouse}={w.pk}} "
					+ "WHERE {s.productCode}=?p AND {w.code}=?w");
			q.addQueryParameter("p", experience.getProduct().getCode());
			q.addQueryParameter("w", WAREHOUSE);
			final List<StockLevelModel> result = flexibleSearchService.<StockLevelModel> search(q).getResult();
			return result.isEmpty() ? null : result.get(0);
		});
		if (level == null)
		{
			return null;
		}
		final Integer remaining = remaining(experience, delta);
		level.setAvailable(remaining == null ? UNLIMITED : remaining.intValue());
		level.setReserved(0);
		return level;
	}

	@Override
	public void syncStock(final SocioExperienceModel experience)
	{
		final StockLevelModel level = adjustStock(experience, 0);
		if (level != null)
		{
			unrestricted(() -> {
				modelService.save(level);
				return null;
			});
		}
	}

	@Override
	public SocioExperienceModel findByProduct(final ProductModel product)
	{
		if (product == null || product.getCode() == null || !product.getCode().startsWith(PRODUCT_PREFIX))
		{
			return null;
		}
		return unrestricted(() -> {
			final FlexibleSearchQuery q = new FlexibleSearchQuery("SELECT {pk} FROM {SocioExperience} WHERE {code}=?c");
			q.addQueryParameter("c", product.getCode().substring(PRODUCT_PREFIX.length()));
			final List<SocioExperienceModel> result = flexibleSearchService.<SocioExperienceModel> search(q).getResult();
			return result.isEmpty() ? null : result.get(0);
		});
	}

	// ------------------------------------------------------------ compra

	@Override
	public List<SocioRedemptionModel> recordPurchase(final AbstractOrderModel order)
	{
		final List<SocioRedemptionModel> created = new ArrayList<>();
		if (order == null || order.getEntries() == null || !(order.getUser() instanceof CustomerModel))
		{
			return created;
		}
		final CustomerModel customer = (CustomerModel) order.getUser();
		return unrestricted(() -> {
			final FlexibleSearchQuery existing = new FlexibleSearchQuery("SELECT {pk} FROM {SocioRedemption} WHERE {orderCode}=?o");
			existing.addQueryParameter("o", order.getCode());
			if (!flexibleSearchService.search(existing).getResult().isEmpty())
			{
				return created; // já gerou (pedido reprocessado)
			}
			for (final AbstractOrderEntryModel entry : order.getEntries())
			{
				final SocioExperienceModel experience = findByProduct(entry.getProduct());
				final int quantity = entry.getQuantity() == null ? 0 : entry.getQuantity().intValue();
				if (experience == null || quantity <= 0)
				{
					continue;
				}
				final double unit = entry.getTotalPrice() == null ? 0 : entry.getTotalPrice().doubleValue() / quantity;
				for (int i = 0; i < quantity; i++)
				{
					final SocioRedemptionModel r = modelService.create(SocioRedemptionModel.class);
					r.setVoucher(newVoucher());
					r.setCustomer(customer);
					r.setExperience(experience);
					r.setPoints(Integer.valueOf(0));
					r.setDate(order.getDate() != null ? order.getDate() : new Date());
					r.setPlan(socioPlanService.memberPlan(customer));
					r.setOrigin(ORIGIN_PURCHASE);
					r.setOrderCode(order.getCode());
					r.setAmount(Double.valueOf(Math.round(unit * 100) / 100.0));
					modelService.save(r);
					created.add(r);
					recordPurchaseEvent(customer, r, experience, order);
				}
				syncStock(experience);
				LOG.info("Pedido {}: {} voucher(s) de {} para {}", order.getCode(), quantity, experience.getCode(), customer.getUid());
			}
			return created;
		});
	}

	protected void recordPurchaseEvent(final CustomerModel customer, final SocioRedemptionModel r,
			final SocioExperienceModel experience, final AbstractOrderModel order)
	{
		try
		{
			final SocioEventModel event = socioPlanService.newEvent(customer, SocioEventType.COMPRA_EXPERIENCIA, "Loja",
					name(experience) + ": compra no pedido " + order.getCode() + " por " + money(r.getAmount().doubleValue())
							+ ", voucher " + r.getVoucher());
			event.setResult("Voucher " + r.getVoucher());
			modelService.save(event);
		}
		catch (final Exception e)
		{
			LOG.warn("Pedido {}: não gravou o evento de compra de {}: {}", order.getCode(), customer.getUid(), e.getMessage());
		}
	}

	// ------------------------------------------------------------ voucher

	@Override
	public SocioRedemptionModel findVoucher(final String voucher)
	{
		if (StringUtils.isBlank(voucher))
		{
			return null;
		}
		return unrestricted(() -> {
			final FlexibleSearchQuery q = new FlexibleSearchQuery("SELECT {pk} FROM {SocioRedemption} WHERE {voucher}=?v");
			q.addQueryParameter("v", voucher.trim().toUpperCase(Locale.ROOT));
			final List<SocioRedemptionModel> result = flexibleSearchService.<SocioRedemptionModel> search(q).getResult();
			return result.isEmpty() ? null : result.get(0);
		});
	}

	@Override
	public SocioRedemptionModel useVoucher(final String voucher, final String author)
	{
		final SocioRedemptionModel r = existing(voucher);
		if (r.getCancelledAt() != null)
		{
			throw new SocioPointsException("O voucher " + r.getVoucher() + " foi cancelado em " + date(r.getCancelledAt()) + ".");
		}
		if (r.getUsedAt() != null)
		{
			throw new SocioPointsException("O voucher " + r.getVoucher() + " já foi utilizado em " + date(r.getUsedAt())
					+ (StringUtils.isBlank(r.getUsedBy()) ? "" : " (" + r.getUsedBy() + ")") + ".");
		}
		r.setUsedAt(new Date());
		r.setUsedBy(StringUtils.defaultIfBlank(author, currentUid()));
		unrestricted(() -> {
			modelService.save(r); // o SocioRedemptionInterceptor grava o evento na linha do tempo
			return null;
		});
		return r;
	}

	@Override
	public SocioRedemptionModel cancelVoucher(final String voucher, final String author, final String reason)
	{
		final SocioRedemptionModel r = existing(voucher);
		if (r.getCancelledAt() != null)
		{
			throw new SocioPointsException("O voucher " + r.getVoucher() + " já estava cancelado.");
		}
		if (r.getUsedAt() != null)
		{
			throw new SocioPointsException("O voucher " + r.getVoucher() + " já foi utilizado e não pode ser cancelado.");
		}
		r.setCancelledAt(new Date());
		r.setCancelledBy(StringUtils.defaultIfBlank(author, currentUid()));
		r.setCancelReason(StringUtils.trimToNull(reason));
		unrestricted(() -> {
			modelService.save(r); // o interceptor devolve a vaga e grava o evento
			return null;
		});
		return r;
	}

	protected SocioRedemptionModel existing(final String voucher)
	{
		final SocioRedemptionModel r = findVoucher(voucher);
		if (r == null)
		{
			throw new SocioPointsException("Voucher " + StringUtils.defaultString(voucher).trim() + " não encontrado.");
		}
		return r;
	}

	protected String newVoucher()
	{
		for (int attempt = 0; attempt < 20; attempt++)
		{
			final StringBuilder code = new StringBuilder("NACAO-");
			for (int i = 0; i < 8; i++)
			{
				if (i == 4)
				{
					code.append('-');
				}
				code.append(VOUCHER_CHARS.charAt(RANDOM.nextInt(VOUCHER_CHARS.length())));
			}
			if (findVoucher(code.toString()) == null)
			{
				return code.toString();
			}
		}
		throw new SocioPointsException("Não foi possível gerar o voucher agora.");
	}

	// ------------------------------------------------------------ cockpit

	@Override
	public List<SocioExperienceModel> allExperiences()
	{
		return unrestricted(() -> flexibleSearchService.<SocioExperienceModel> search(
				new FlexibleSearchQuery("SELECT {pk} FROM {SocioExperience} ORDER BY {sortOrder}, {points}, {code}")).getResult());
	}

	@Override
	public SocioExperienceOverview overview(final int months)
	{
		return unrestricted(() -> {
			final SocioExperienceOverview o = new SocioExperienceOverview();
			o.generatedAt = new Date();
			o.saleEnabled = isSaleEnabled();
			final java.time.ZoneId zone = java.time.ZoneId.of("America/Sao_Paulo");
			final java.time.YearMonth current = java.time.YearMonth.now(zone);
			final java.util.Map<String, SocioExperienceOverview.Month> byMonth = new java.util.LinkedHashMap<>();
			for (int i = Math.max(1, months) - 1; i >= 0; i--)
			{
				final SocioExperienceOverview.Month m = new SocioExperienceOverview.Month();
				m.key = current.minusMonths(i).toString();
				byMonth.put(m.key, m);
			}
			o.months.addAll(byMonth.values());
			final java.util.Map<SocioExperienceModel, SocioExperienceOverview.Line> lines = new java.util.LinkedHashMap<>();
			for (final SocioExperienceModel e : allExperiences())
			{
				final SocioExperienceOverview.Line l = new SocioExperienceOverview.Line();
				l.code = e.getCode();
				l.name = name(e);
				l.description = e.getDescription(PT);
				l.active = !Boolean.FALSE.equals(e.getActive());
				l.points = e.getPoints() == null ? 0 : e.getPoints().intValue();
				l.price = e.getPrice();
				l.stock = e.getStock();
				l.productCode = e.getProduct() == null ? null : e.getProduct().getCode();
				l.onSale = o.saleEnabled && l.active && l.productCode != null && l.price != null && l.price.doubleValue() > 0;
				l.minPlanName = e.getMinPlan() == null ? null
						: StringUtils.defaultIfBlank(e.getMinPlan().getName(PT), e.getMinPlan().getCode());
				l.statusMessage = e.getStatusMessage();
				lines.put(e, l);
				o.lines.add(l);
			}
			for (final SocioRedemptionModel r : flexibleSearchService.<SocioRedemptionModel> search(
					new FlexibleSearchQuery("SELECT {pk} FROM {SocioRedemption}")).getResult())
			{
				final SocioExperienceOverview.Line l = lines.get(r.getExperience());
				if (l == null)
				{
					continue;
				}
				if (r.getCancelledAt() != null)
				{
					l.cancelled++;
					continue;
				}
				final boolean purchase = isPurchase(r);
				final int points = r.getPoints() == null ? 0 : r.getPoints().intValue();
				final double amount = r.getAmount() == null ? 0 : r.getAmount().doubleValue();
				if (purchase)
				{
					l.purchases++;
					l.revenue += amount;
				}
				else
				{
					l.redemptions++;
					l.pointsUsed += points;
				}
				if (r.getUsedAt() != null)
				{
					l.used++;
				}
				final Date when = r.getDate() != null ? r.getDate() : r.getCreationtime();
				final SocioExperienceOverview.Month m = when == null ? null
						: byMonth.get(java.time.YearMonth.from(when.toInstant().atZone(zone)).toString());
				if (m != null)
				{
					if (purchase)
					{
						m.purchases++;
						m.revenue += amount;
					}
					else
					{
						m.redemptions++;
						m.pointsUsed += points;
					}
				}
			}
			for (final SocioExperienceOverview.Line l : o.lines)
			{
				l.remaining = l.stock == null ? null : Integer.valueOf(Math.max(0, l.stock.intValue() - l.redemptions - l.purchases));
				o.total.redemptions += l.redemptions;
				o.total.purchases += l.purchases;
				o.total.used += l.used;
				o.total.cancelled += l.cancelled;
				o.total.pointsUsed += l.pointsUsed;
				o.total.revenue += l.revenue;
				if (l.stock != null)
				{
					o.total.stock = Integer.valueOf((o.total.stock == null ? 0 : o.total.stock.intValue()) + l.stock.intValue());
					o.total.remaining = Integer.valueOf((o.total.remaining == null ? 0 : o.total.remaining.intValue())
							+ l.remaining.intValue());
				}
			}
			return o;
		});
	}

	@Override
	public List<SocioRedemptionModel> searchRedemptions(final String experienceCode, final String status, final String origin,
			final String text)
	{
		return unrestricted(() -> {
			final List<SocioRedemptionModel> all = flexibleSearchService.<SocioRedemptionModel> search(new FlexibleSearchQuery(
					"SELECT {pk} FROM {SocioRedemption} ORDER BY {date} DESC, {creationtime} DESC")).getResult();
			final String needle = StringUtils.trimToEmpty(text).toLowerCase(Locale.ROOT);
			final List<SocioRedemptionModel> result = new ArrayList<>();
			for (final SocioRedemptionModel r : all)
			{
				if (StringUtils.isNotBlank(experienceCode)
						&& (r.getExperience() == null || !experienceCode.equals(r.getExperience().getCode())))
				{
					continue;
				}
				if ("VALID".equals(status) && (r.getCancelledAt() != null || r.getUsedAt() != null)
						|| "USED".equals(status) && (r.getUsedAt() == null || r.getCancelledAt() != null)
						|| "CANCELLED".equals(status) && r.getCancelledAt() == null)
				{
					continue;
				}
				if ("PURCHASE".equals(origin) && !isPurchase(r) || "POINTS".equals(origin) && isPurchase(r))
				{
					continue;
				}
				if (!needle.isEmpty())
				{
					final String hay = (StringUtils.defaultString(r.getVoucher()) + " "
							+ (r.getCustomer() == null ? ""
									: StringUtils.defaultString(r.getCustomer().getUid()) + " "
											+ StringUtils.defaultString(r.getCustomer().getName()))
							+ " " + StringUtils.defaultString(r.getOrderCode())).toLowerCase(Locale.ROOT);
					if (!hay.contains(needle))
					{
						continue;
					}
				}
				result.add(r);
			}
			return result;
		});
	}

	protected static boolean isPurchase(final SocioRedemptionModel r)
	{
		return StringUtils.isNotBlank(r.getOrderCode()) || ORIGIN_PURCHASE.equals(r.getOrigin());
	}

	// ------------------------------------------------------------ apoio

	protected ProductModel findProduct(final String code)
	{
		final CatalogVersionModel online = catalogVersionService.getCatalogVersion(CATALOG, "Online");
		final FlexibleSearchQuery query = new FlexibleSearchQuery("SELECT {pk} FROM {Product} WHERE {code}=?c AND {catalogVersion}=?cv");
		query.addQueryParameter("c", code);
		query.addQueryParameter("cv", online);
		final List<ProductModel> result = flexibleSearchService.<ProductModel> search(query).getResult();
		return result.isEmpty() ? null : result.get(0);
	}

	protected void runImpex(final List<String> lines, final String code)
	{
		final ImportConfig config = new ImportConfig();
		config.setScript(new StreamBasedImpExResource(
				new ByteArrayInputStream(String.join("\n", lines).getBytes(StandardCharsets.UTF_8)), "UTF-8"));
		config.setSynchronous(true);
		config.setLegacyMode(Boolean.FALSE);
		// o ImpEx abre e fecha uma sessão nesta thread; no Backoffice isso apaga o contexto da tela
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
			LOG.error("Falha no ImpEx da experiência {}:\n{}", code, detail);
			throw new IllegalStateException("Não foi possível gravar o produto " + code + ". "
					+ StringUtils.abbreviate(detail.replaceAll("\\s+", " "), 400));
		}
	}

	/** O produto novo ou alterado aparece na busca da loja */
	protected void reindex()
	{
		try
		{
			final CronJobModel cronJob = cronJobService.getCronJob("full-electronicsIndex-cronJob");
			if (!cronJobService.isRunning(cronJob))
			{
				cronJobService.performCronJob(cronJob, false);
			}
		}
		catch (final RuntimeException e)
		{
			LOG.warn("Não foi possível disparar a reindexação: {}", e.getMessage());
		}
	}

	/** Valor de célula de ImpEx: entre aspas, com aspas dobradas */
	protected static String q(final String value)
	{
		return "\"" + StringUtils.defaultString(value).replace("\"", "\"\"").replaceAll("[\\r\\n]+", " ") + "\"";
	}

	protected static String name(final SocioExperienceModel experience)
	{
		return StringUtils.defaultIfBlank(experience.getName(PT), experience.getCode());
	}

	protected static String number(final int value)
	{
		return NumberFormat.getIntegerInstance(new Locale("pt", "BR")).format(value);
	}

	protected static String money(final double value)
	{
		final NumberFormat format = NumberFormat.getNumberInstance(new Locale("pt", "BR"));
		format.setMinimumFractionDigits(2);
		format.setMaximumFractionDigits(2);
		return "R$ " + format.format(value);
	}

	protected static String date(final Date date)
	{
		final java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("pt", "BR"));
		format.setTimeZone(java.util.TimeZone.getTimeZone("America/Sao_Paulo"));
		return format.format(date);
	}

	protected String currentUid()
	{
		final UserModel user = userService.getCurrentUser();
		return user == null ? null : user.getUid();
	}

	protected <T> T unrestricted(final Supplier<T> body)
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

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
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

	public void setSessionService(final SessionService sessionService)
	{
		this.sessionService = sessionService;
	}

	public void setSearchRestrictionService(final SearchRestrictionService searchRestrictionService)
	{
		this.searchRestrictionService = searchRestrictionService;
	}

	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}

	public void setSocioPlanService(final SocioPlanService socioPlanService)
	{
		this.socioPlanService = socioPlanService;
	}
}
