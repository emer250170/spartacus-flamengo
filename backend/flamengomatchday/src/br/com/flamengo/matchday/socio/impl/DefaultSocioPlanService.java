package br.com.flamengo.matchday.socio.impl;

import br.com.flamengo.matchday.enums.SocioPlanStatus;
import br.com.flamengo.matchday.model.SocioPlanModel;
import br.com.flamengo.matchday.socio.SocioPlanService;
import de.hybris.platform.catalog.CatalogVersionService;
import de.hybris.platform.catalog.model.CatalogVersionModel;
import de.hybris.platform.core.Registry;
import de.hybris.platform.core.Tenant;
import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.core.model.c2l.CurrencyModel;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.product.UnitModel;
import de.hybris.platform.core.model.security.PrincipalGroupModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.core.model.user.UserGroupModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.cronjob.model.CronJobModel;
import de.hybris.platform.enumeration.EnumerationService;
import de.hybris.platform.europe1.model.PriceRowModel;
import de.hybris.platform.impex.model.ImpExMediaModel;
import de.hybris.platform.servicelayer.cronjob.CronJobService;
import de.hybris.platform.servicelayer.i18n.CommonI18NService;
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
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Publica os planos de sócio-torcedor na loja.
 * <p>
 * Produto, vagas, categoria e promoção vão por ImpEx; plano de preço e
 * benefícios por modelos genéricos (a extensão não depende das extensões de
 * assinatura e de regras, que já estão no servidor). A promoção é publicada no
 * módulo de regras numa thread separada, depois de gravar o plano (publicar dentro
 * da transação trava). As regras que a loja precisa conhecer (onda, desconto,
 * convidados, grupos) vão num comentário no fim da descrição do produto.
 */
public class DefaultSocioPlanService implements SocioPlanService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultSocioPlanService.class);

	private static final Locale PT = new Locale("pt");
	private static final Locale EN = Locale.ENGLISH;
	private static final String CATALOG = "flamengoProductCatalog";
	private static final String CATEGORY_ON = "socio_torcedor";
	private static final String CATEGORY_OFF = "socio_torcedor_fora";
	private static final String TERM = "nacao-anual";
	private static final String WAREHOUSE = "warehouse_s";
	private static final String GUEST_PREFIX = "NACAO-CONVIDADO-";
	private static final String PROMOTION_GROUP = "electronicsPromoGrp";
	private static final String RULE_GROUP = "customerPromotionRuleGroup";
	private static final String RULE_MODULE = "promotions-module";
	/** Categorias das poltronas: o desconto vale só no ingresso */
	private static final String[] SECTORS = { "setor-norte", "setor-sul", "setor-leste-inf", "setor-oeste-sup" };
	/** Grupo sem membros: promoção de plano que passou a ter 0% de desconto não vale para ninguém */
	private static final String NO_DISCOUNT_GROUP = "socio-sem-desconto";
	private static final int UNLIMITED = 100000;
	/** Benefícios comuns a todos os planos (Entitlement criados no cadastro do sócio-torcedor) */
	private static final String[] COMMON_ENTITLEMENTS = { "nacao-frete-gratis", "nacao-cashback", "nacao-personalizacao",
			"nacao-premios", "nacao-parceiros", "nacao-pacote-maracana" };

	private FlexibleSearchService flexibleSearchService;
	private ModelService modelService;
	private UserService userService;
	private SessionService sessionService;
	private CatalogVersionService catalogVersionService;
	private CommonI18NService commonI18NService;
	private ImportService importService;
	private MediaService mediaService;
	private CronJobService cronJobService;
	private EnumerationService enumerationService;

	// ------------------------------------------------------------ consultas

	@Override
	public List<SocioPlanModel> getPlans()
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery(
				"SELECT {pk} FROM {SocioPlan} ORDER BY {sortOrder}, {code}");
		return flexibleSearchService.<SocioPlanModel> search(query).getResult();
	}

	@Override
	public SocioPlanModel findByCode(final String code)
	{
		if (StringUtils.isBlank(code))
		{
			return null;
		}
		final FlexibleSearchQuery query = new FlexibleSearchQuery("SELECT {pk} FROM {SocioPlan} WHERE {code}=?c");
		query.addQueryParameter("c", code);
		final List<SocioPlanModel> result = flexibleSearchService.<SocioPlanModel> search(query).getResult();
		return result.isEmpty() ? null : result.get(0);
	}

	@Override
	public SocioPlanModel findForProduct(final ProductModel product)
	{
		return product == null ? null : findByCode(product.getCode());
	}

	@Override
	public String generateCode(final SocioPlanModel plan)
	{
		final String base = slug(StringUtils.defaultIfBlank(plan.getName(PT), "PLANO")).toUpperCase(Locale.ROOT);
		String code = base;
		int i = 2;
		while (findByCode(code) != null || findProduct(code) != null)
		{
			code = base + "-" + i++;
		}
		return code;
	}

	@Override
	public int countMembers(final SocioPlanModel plan)
	{
		final UserGroupModel group = plan.getCustomerGroup();
		if (group == null || group.getMembers() == null)
		{
			return 0;
		}
		final UserGroupModel pending = plan.getPendingGroup();
		int count = 0;
		for (final Object member : group.getMembers())
		{
			if (member instanceof UserModel && (pending == null || ((UserModel) member).getGroups() == null
					|| !((UserModel) member).getGroups().contains(pending)))
			{
				count++;
			}
		}
		return count;
	}

	// ------------------------------------------------------------ pedido

	@Override
	public void applyOrder(final AbstractOrderModel order)
	{
		if (order == null || order.getEntries() == null || !(order.getUser() instanceof CustomerModel))
		{
			return;
		}
		final List<SocioPlanModel> bought = new ArrayList<>();
		for (final AbstractOrderEntryModel entry : order.getEntries())
		{
			final SocioPlanModel plan = findForProduct(entry.getProduct());
			if (plan != null && plan.getCustomerGroup() != null)
			{
				bought.add(plan);
			}
		}
		if (bought.isEmpty())
		{
			return;
		}
		bought.sort(Comparator.comparingInt(DefaultSocioPlanService::waveRank)
				.thenComparing(p -> -(p.getMonthlyPrice() == null ? 0 : p.getMonthlyPrice().doubleValue())));
		final SocioPlanModel best = bought.get(0);
		final CustomerModel customer = (CustomerModel) order.getUser();
		final Set<String> planGroups = new HashSet<>();
		for (final SocioPlanModel plan : getPlans())
		{
			if (plan.getCustomerGroup() != null)
			{
				planGroups.add(plan.getCustomerGroup().getUid());
			}
			if (plan.getPendingGroup() != null)
			{
				planGroups.add(plan.getPendingGroup().getUid());
			}
		}
		final Set<PrincipalGroupModel> groups = new HashSet<>();
		if (customer.getGroups() != null)
		{
			for (final PrincipalGroupModel group : customer.getGroups())
			{
				if (!planGroups.contains(group.getUid()))
				{
					groups.add(group);
				}
			}
		}
		groups.add(best.getCustomerGroup());
		customer.setGroups(groups);
		modelService.save(customer);
		LOG.info("Pedido {}: {} agora é sócio {}", order.getCode(), customer.getUid(), best.getCode());
	}

	/** Onda 1 primeiro; sem prioridade (0) por último */
	protected static int waveRank(final SocioPlanModel plan)
	{
		final int wave = plan.getWave() == null ? 0 : plan.getWave().intValue();
		return wave <= 0 ? 99 : wave;
	}

	// ------------------------------------------------------------ publicação

	@Override
	public String publish(final SocioPlanModel plan)
	{
		// tudo como admin: o gestor do programa só precisa de acesso ao plano
		return sessionService.executeInLocalView(new SessionExecutionBody()
		{
			@Override
			public Object execute()
			{
				userService.setCurrentUser(userService.getAdminUser());
				return doPublish(plan);
			}
		});
	}

	protected String doPublish(final SocioPlanModel plan)
	{
		if (StringUtils.isBlank(plan.getCode()))
		{
			plan.setCode(generateCode(plan));
		}
		ensureGroups(plan);
		final boolean onSale = plan.getStatus() == SocioPlanStatus.ON_SALE;
		if (!onSale && plan.getProduct() == null)
		{
			return "Rascunho salvo. O plano entra na loja quando a situação for \"À venda\".";
		}
		final int guests = intOf(plan.getMaxGuests());
		final String guestCode = guests > 0 ? ensureGuest(doubleOf(plan.getGuestPrice())) : null;
		importProduct(plan, onSale, guestCode);
		final ProductModel product = findProduct(plan.getCode());
		if (product == null)
		{
			throw new IllegalStateException("O produto " + plan.getCode() + " não foi criado.");
		}
		plan.setProduct(product);
		replacePricePlan(product, doubleOf(plan.getMonthlyPrice()), plan.getCode());
		replaceEntitlements(product, plan, guestCode);
		final boolean publishRule = upsertPromotion(plan);
		reindex();
		if (publishRule)
		{
			schedulePromotionPublish(plan.getPromotionCode());
		}

		final StringBuilder message = new StringBuilder();
		if (onSale)
		{
			message.append("À venda na loja: ").append(money(doubleOf(plan.getMonthlyPrice()))).append("/mês");
			message.append(plan.getSlots() == null ? ", sem limite de vagas" : ", " + plan.getSlots() + " vagas");
			if (intOf(plan.getTicketDiscount()) > 0)
			{
				message.append(", ").append(plan.getTicketDiscount()).append("% no ingresso");
			}
			message.append(intOf(plan.getWave()) > 0 ? ", onda " + plan.getWave() : ", sem prioridade de compra");
			if (guestCode != null)
			{
				message.append(", até ").append(guests).append(" convidado(s) (").append(guestCode).append(')');
			}
			message.append('.');
		}
		else
		{
			message.append("Fora da loja: o plano não aparece mais para novas assinaturas; quem já é sócio continua com os benefícios.");
		}
		if (publishRule)
		{
			message.append(" Promoção de desconto sendo publicada (leva alguns segundos).");
		}
		message.append(" A busca da loja atualiza em alguns minutos.");
		return message.toString();
	}

	/** Grupo dos titulares e marcador de carrinho, dentro de customergroup (a API da loja só lista esses) */
	protected void ensureGroups(final SocioPlanModel plan)
	{
		final String slug = slug(plan.getCode()).toLowerCase(Locale.ROOT);
		final String name = StringUtils.defaultIfBlank(plan.getName(PT), plan.getCode());
		if (plan.getCustomerGroup() == null)
		{
			plan.setCustomerGroup(group("socio-" + slug, "Sócio " + name, name + " member", true));
		}
		if (plan.getPendingGroup() == null)
		{
			plan.setPendingGroup(group("socio-pendente-" + slug, name + " no carrinho", name + " in cart", true));
		}
	}

	protected UserGroupModel group(final String uid, final String namePt, final String nameEn, final boolean customerGroup)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery("SELECT {pk} FROM {UserGroup} WHERE {uid}=?u");
		query.addQueryParameter("u", uid);
		final List<UserGroupModel> found = flexibleSearchService.<UserGroupModel> search(query).getResult();
		if (!found.isEmpty())
		{
			return found.get(0);
		}
		final UserGroupModel group = modelService.create(UserGroupModel.class);
		group.setUid(uid);
		group.setLocName(namePt, PT);
		group.setLocName(nameEn, EN);
		if (customerGroup)
		{
			group.setGroups(Collections.singleton(userService.getUserGroupForUID("customergroup")));
		}
		modelService.save(group);
		return group;
	}

	/** Produto do convidado pelo valor da mensalidade (NACAO-CONVIDADO-60); reaproveita o que já existe */
	protected String ensureGuest(final double price)
	{
		final String code = GUEST_PREFIX + (price == Math.rint(price) ? String.valueOf((long) price)
				: String.format(Locale.ROOT, "%.2f", price).replace('.', '-'));
		if (findProduct(code) != null)
		{
			return code;
		}
		final String namePt = "Convidado sócio-torcedor · " + money(price) + "/mês";
		final String nameEn = "Membership guest · " + money(price) + "/month";
		final String summaryPt = "Convidado do titular · compra na mesma prioridade";
		final List<String> lines = header();
		lines.add("INSERT_UPDATE SubscriptionProduct;code[unique=true];$cv;name[lang=pt];name[lang=en];summary[lang=pt];"
				+ "summary[lang=en];description[lang=pt];description[lang=en];supercategories(code,$cv);subscriptionTerm(id);"
				+ "unit(code)[default=pieces];approvalstatus(code)");
		lines.add(";" + code + ";;" + q(namePt) + ";" + q(nameEn) + ";" + q(summaryPt) + ";" + q("Guest of the member · same priority")
				+ ";" + q("<p>" + summaryPt + "</p>") + ";" + q("<p>Guest of the member · same priority</p>") + ";" + CATEGORY_ON + ";"
				+ TERM + ";;approved");
		lines.addAll(stockLines(code, UNLIMITED));
		runImpex(lines, code);
		replacePricePlan(findProduct(code), price, code);
		return code;
	}

	protected void importProduct(final SocioPlanModel plan, final boolean onSale, final String guestCode)
	{
		final String code = plan.getCode();
		final String namePt = StringUtils.defaultIfBlank(plan.getName(PT), code);
		final String nameEn = StringUtils.defaultIfBlank(plan.getName(EN), namePt);
		final String summaryPt = StringUtils.defaultIfBlank(plan.getSummary(PT), autoSummary(plan, true));
		final String summaryEn = StringUtils.defaultIfBlank(plan.getSummary(EN), autoSummary(plan, false));
		final String marker = marker(plan, guestCode);
		final String descPt = benefitsHtml(plan.getBenefits(PT)) + marker;
		final String descEn = benefitsHtml(StringUtils.defaultIfBlank(plan.getBenefits(EN), plan.getBenefits(PT))) + marker;
		final int slots = plan.getSlots() == null ? UNLIMITED : Math.max(0, plan.getSlots().intValue() - countMembers(plan));

		final List<String> lines = header();
		lines.add("INSERT_UPDATE Category;code[unique=true];$cv;name[lang=pt];name[lang=en]");
		lines.add(";" + CATEGORY_ON + ";;Sócio-Torcedor;Membership");
		lines.add(";" + CATEGORY_OFF + ";;Sócio-Torcedor (fora da loja);Membership (off sale)");
		lines.add("INSERT_UPDATE SubscriptionProduct;code[unique=true];$cv;name[lang=pt];name[lang=en];summary[lang=pt];"
				+ "summary[lang=en];description[lang=pt];description[lang=en];supercategories(code,$cv);subscriptionTerm(id);"
				+ "unit(code)[default=pieces];approvalstatus(code)");
		lines.add(";" + code + ";;" + q(namePt) + ";" + q(nameEn) + ";" + q(summaryPt) + ";" + q(summaryEn) + ";" + q(descPt) + ";"
				+ q(descEn) + ";" + (onSale ? CATEGORY_ON : CATEGORY_OFF) + ";" + TERM + ";;" + (onSale ? "approved" : "unapproved"));
		lines.addAll(stockLines(code, slots));
		runImpex(lines, code);
	}

	protected List<String> header()
	{
		final List<String> lines = new ArrayList<>();
		lines.add("$cv=catalogVersion(catalog(id[default=" + CATALOG + "]),version[default=Online])[unique=true,default=" + CATALOG
				+ ":Online]");
		return lines;
	}

	protected List<String> stockLines(final String code, final int available)
	{
		final List<String> lines = new ArrayList<>();
		lines.add("INSERT_UPDATE StockLevel;productCode[unique=true];warehouse(code)[unique=true,default=" + WAREHOUSE
				+ "];available;reserved[default=0];preOrder[default=0];maxPreOrder[default=0];maxStockLevelHistoryCount[default=0];"
				+ "inStockStatus(code)[default=notSpecified]");
		lines.add(";" + code + ";;" + available + ";;;;;");
		return lines;
	}

	/** 1ª mensalidade paga na hora + mensalidades 2 a 12 recorrentes (como no cadastro original) */
	protected void replacePricePlan(final ProductModel product, final double price, final String code)
	{
		for (final PriceRowModel row : new ArrayList<>(product.getEurope1Prices()))
		{
			if ("SubscriptionPricePlan".equals(row.getItemtype()))
			{
				removeAll(modelService.getAttributeValue(row, "oneTimeChargeEntries"));
				removeAll(modelService.getAttributeValue(row, "recurringChargeEntries"));
				modelService.remove(row);
			}
		}
		final CurrencyModel brl = commonI18NService.getCurrency("BRL");
		final PriceRowModel plan = modelService.create("SubscriptionPricePlan");
		plan.setProduct(product);
		plan.setCurrency(brl);
		plan.setPrice(Double.valueOf(price));
		plan.setUnit(unit("pieces"));
		plan.setNet(Boolean.FALSE);
		modelService.setAttributeValue(plan, "name",
				localized(StringUtils.defaultString(product.getName(PT), code) + " · " + money(price) + "/mês",
						StringUtils.defaultString(product.getName(EN), code) + " · " + money(price) + "/month"));
		modelService.save(plan);

		final ItemModel oneTime = modelService.create("OneTimeChargeEntry");
		modelService.setAttributeValue(oneTime, "id", code + "-adesao");
		modelService.setAttributeValue(oneTime, "name", localized("1ª mensalidade", "1st monthly fee"));
		modelService.setAttributeValue(oneTime, "billingEvent", item("SELECT {pk} FROM {BillingEvent} WHERE {code}='paynow'"));
		modelService.setAttributeValue(oneTime, "price", Double.valueOf(price));
		modelService.setAttributeValue(oneTime, "subscriptionPricePlanOneTime", plan);
		modelService.save(oneTime);

		final ItemModel recurring = modelService.create("RecurringChargeEntry");
		modelService.setAttributeValue(recurring, "id", code + "-mensal");
		modelService.setAttributeValue(recurring, "cycleStart", Integer.valueOf(2));
		modelService.setAttributeValue(recurring, "cycleEnd", Integer.valueOf(12));
		modelService.setAttributeValue(recurring, "price", Double.valueOf(price));
		modelService.setAttributeValue(recurring, "subscriptionPricePlanRecurring", plan);
		modelService.save(recurring);
	}

	/** Benefícios da assinatura (Entitlement do SAP Commerce): onda, desconto, convidados e os comuns */
	protected void replaceEntitlements(final ProductModel product, final SocioPlanModel plan, final String guestCode)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery(
				"SELECT {pk} FROM {ProductEntitlement} WHERE {subscriptionProduct}=?p");
		query.addQueryParameter("p", product);
		removeAll(flexibleSearchService.search(query).getResult());
		final int wave = intOf(plan.getWave());
		final int discount = intOf(plan.getTicketDiscount());
		final int guests = intOf(plan.getMaxGuests());
		if (wave > 0)
		{
			entitlement(product, "nacao-prioridade-ingresso", Integer.valueOf(wave), "Onda " + wave, "Prioridade " + wave + " · onda "
					+ wave + " de compra de ingressos");
		}
		if (discount > 0)
		{
			entitlement(product, "nacao-desconto-ingresso", Integer.valueOf(discount), null, discount + "% de desconto na inteira");
		}
		if (guests > 0)
		{
			entitlement(product, "nacao-convidados", Integer.valueOf(guests), guestCode, "Até " + guests + " convidado(s)");
		}
		for (final String id : COMMON_ENTITLEMENTS)
		{
			entitlement(product, id, null, null, null);
		}
	}

	protected void entitlement(final ProductModel product, final String id, final Integer quantity, final String condition,
			final String description)
	{
		final ItemModel entitlement = item("SELECT {pk} FROM {Entitlement} WHERE {id}='" + id + "'");
		if (entitlement == null)
		{
			return;
		}
		final ItemModel link = modelService.create("ProductEntitlement");
		modelService.setAttributeValue(link, "id", product.getCode() + "-" + id);
		modelService.setAttributeValue(link, "entitlement", entitlement);
		modelService.setAttributeValue(link, "subscriptionProduct", product);
		modelService.setAttributeValue(link, "catalogVersion", product.getCatalogVersion());
		if (quantity != null)
		{
			modelService.setAttributeValue(link, "quantity", quantity);
		}
		if (condition != null)
		{
			modelService.setAttributeValue(link, "conditionString", condition);
		}
		if (description != null)
		{
			modelService.setAttributeValue(link, "description", localized(description, description));
		}
		modelService.save(link);
	}

	/**
	 * Promoção "X% no ingresso" para o grupo do plano (categorias das poltronas).
	 *
	 * @return true se mudou e precisa publicar no módulo de regras
	 */
	protected boolean upsertPromotion(final SocioPlanModel plan)
	{
		if (StringUtils.isBlank(plan.getPromotionCode()))
		{
			plan.setPromotionCode("socio_" + slug(plan.getCode()).toLowerCase(Locale.ROOT).replace('-', '_') + "_desconto_ingresso");
		}
		final String code = plan.getPromotionCode();
		final int discount = intOf(plan.getTicketDiscount());
		final ItemModel existing = item("SELECT {pk} FROM {PromotionSourceRule} WHERE {code}='" + code.replace("'", "") + "'");
		if (existing == null && discount <= 0)
		{
			return false;
		}
		final String groupUid;
		if (discount > 0)
		{
			groupUid = plan.getCustomerGroup().getUid();
		}
		else
		{
			group(NO_DISCOUNT_GROUP, "Sem desconto de sócio (sem membros)", "No member discount (no members)", false);
			groupUid = NO_DISCOUNT_GROUP;
		}
		if (existing != null)
		{
			final String conditions = StringUtils.defaultString((String) modelService.getAttributeValue(existing, "conditions"));
			final String actions = StringUtils.defaultString((String) modelService.getAttributeValue(existing, "actions"));
			final Object status = modelService.getAttributeValue(existing, "status");
			if (conditions.contains("\"" + groupUid + "\"") && actions.replace(" ", "").contains("\"value\":" + discount)
					&& status != null && "PUBLISHED".equals(status.toString()))
			{
				return false;
			}
		}
		final String name = StringUtils.defaultIfBlank(plan.getName(PT), plan.getCode());
		final String conditions = "[{\"definitionId\":\"y_target_customers\",\"parameters\":{"
				+ "\"customer_groups_operator\":{\"type\":\"Enum(de.hybris.platform.ruledefinitions.CollectionOperator)\",\"value\":\"CONTAINS_ANY\"},"
				+ "\"customer_groups\":{\"type\":\"List(ItemType(UserGroup))\",\"value\":[\"" + groupUid + "\"]},"
				+ "\"customers\":{\"type\":\"List(ItemType(Customer))\",\"value\":[]},"
				+ "\"excluded_customer_groups\":{\"type\":\"List(ItemType(UserGroup))\",\"value\":[]},"
				+ "\"excluded_customers\":{\"type\":\"List(ItemType(Customer))\",\"value\":[]}},\"children\":[]},"
				+ "{\"definitionId\":\"y_qualifying_categories\",\"parameters\":{"
				+ "\"quantity\":{\"type\":\"java.lang.Integer\",\"value\":1},"
				+ "\"operator\":{\"type\":\"Enum(de.hybris.platform.ruledefinitions.AmountOperator)\",\"value\":\"GREATER_THAN_OR_EQUAL\"},"
				+ "\"categories_operator\":{\"type\":\"Enum(de.hybris.platform.ruledefinitions.CollectionOperator)\",\"value\":\"CONTAINS_ANY\"},"
				+ "\"categories\":{\"type\":\"List(ItemType(Category))\",\"value\":[\"" + String.join("\",\"", SECTORS) + "\"]},"
				+ "\"excluded_categories\":{\"type\":\"List(ItemType(Category))\",\"value\":[]},"
				+ "\"excluded_products\":{\"type\":\"List(ItemType(Product))\",\"value\":[]}},\"children\":[]}]";
		final String actions = "[{\"definitionId\":\"y_order_entry_percentage_discount\",\"parameters\":{"
				+ "\"value\":{\"type\":\"java.math.BigDecimal\",\"value\":" + discount + "}}}]";
		final List<String> lines = new ArrayList<>();
		lines.add("INSERT_UPDATE PromotionSourceRule;code[unique=true];name[lang=pt];name[lang=en];description[lang=pt];"
				+ "messageFired[lang=pt];messageFired[lang=en];priority;stackable;website(Identifier);ruleGroup(code);conditions;actions");
		lines.add(";" + code + ";" + q(name + ": " + discount + "% no ingresso") + ";" + q(name + ": " + discount + "% off tickets") + ";"
				+ q("Sócio " + name + " paga " + (100 - discount) + "% da inteira") + ";"
				+ q("Sócio " + name + ": " + discount + "% de desconto no ingresso") + ";"
				+ q(name + " member: " + discount + "% off tickets") + ";100;false;" + PROMOTION_GROUP + ";" + RULE_GROUP + ";" + q(conditions)
				+ ";" + q(actions));
		runImpex(lines, code);
		return true;
	}

	/** Publica a promoção depois que o plano foi gravado (dentro da transação do salvamento trava) */
	protected void schedulePromotionPublish(final String code)
	{
		final Tenant tenant = Registry.getCurrentTenant();
		final Thread thread = new Thread(() -> {
			Registry.setCurrentTenant(tenant);
			try
			{
				Thread.sleep(4000);
				sessionService.executeInLocalView(new SessionExecutionBody()
				{
					@Override
					public void executeWithoutResult()
					{
						userService.setCurrentUser(userService.getAdminUser());
						publishRule(code);
					}
				});
			}
			catch (final InterruptedException e)
			{
				Thread.currentThread().interrupt();
			}
			catch (final Exception e)
			{
				LOG.error("Falha ao publicar a promoção " + code, e);
			}
			finally
			{
				Registry.unsetCurrentTenant();
			}
		}, "socio-promocao-" + code);
		thread.setDaemon(true);
		thread.start();
	}

	protected void publishRule(final String code)
	{
		final ItemModel rule = item("SELECT {pk} FROM {PromotionSourceRule} WHERE {code}='" + code.replace("'", "") + "'");
		if (rule == null)
		{
			return;
		}
		try
		{
			final Object maintenance = Registry.getApplicationContext().getBean("ruleMaintenanceService");
			Method compile = null;
			for (final Method method : maintenance.getClass().getMethods())
			{
				if ("compileAndPublishRulesWithBlocking".equals(method.getName()) && method.getParameterCount() == 3)
				{
					compile = method;
				}
			}
			if (compile == null)
			{
				LOG.error("ruleMaintenanceService sem compileAndPublishRulesWithBlocking");
				return;
			}
			final Object result = compile.invoke(maintenance, Collections.singletonList(rule), RULE_MODULE, Boolean.TRUE);
			modelService.refresh(rule);
			modelService.setAttributeValue(rule, "status", enumerationService.getEnumerationValue("RuleStatus", "PUBLISHED"));
			modelService.save(rule);
			LOG.info("Promoção {} publicada: {}", code, result);
		}
		catch (final Exception e)
		{
			LOG.error("Falha ao publicar a promoção " + code, e);
		}
	}

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

	// ------------------------------------------------------------ textos

	/** Comentário no fim da descrição com as regras que a loja usa (onda, desconto, convidados, grupos) */
	protected String marker(final SocioPlanModel plan, final String guestCode)
	{
		final Map<String, Object> rules = new LinkedHashMap<>();
		rules.put("wave", Integer.valueOf(Math.max(0, intOf(plan.getWave()))));
		rules.put("discount", Integer.valueOf(intOf(plan.getTicketDiscount())));
		rules.put("maxGuests", Integer.valueOf(guestCode == null ? 0 : intOf(plan.getMaxGuests())));
		rules.put("guest", guestCode);
		rules.put("group", plan.getCustomerGroup() == null ? null : plan.getCustomerGroup().getUid());
		rules.put("pending", plan.getPendingGroup() == null ? null : plan.getPendingGroup().getUid());
		rules.put("family", StringUtils.defaultIfBlank(plan.getFamily(), "Nação Maraca"));
		rules.put("order", Integer.valueOf(plan.getSortOrder() == null ? 100 : plan.getSortOrder().intValue()));
		final StringBuilder json = new StringBuilder("{");
		for (final Map.Entry<String, Object> entry : rules.entrySet())
		{
			if (json.length() > 1)
			{
				json.append(',');
			}
			json.append('"').append(entry.getKey()).append("\":");
			final Object value = entry.getValue();
			if (value == null)
			{
				json.append("null");
			}
			else if (value instanceof Number)
			{
				json.append(value);
			}
			else
			{
				json.append('"').append(value.toString().replace("\\", "\\\\").replace("\"", "\\\"").replace("--", "- -")).append('"');
			}
		}
		return "<!--" + MARKER + " " + json.append('}') + "-->";
	}

	protected String benefitsHtml(final String benefits)
	{
		final StringBuilder html = new StringBuilder("<ul>");
		for (final String line : StringUtils.defaultString(benefits).split("\\r?\\n"))
		{
			final String item = line.replaceFirst("^\\s*[-•*]\\s*", "").trim();
			if (!item.isEmpty())
			{
				html.append("<li>").append(item.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")).append("</li>");
			}
		}
		return html.append("</ul>").toString();
	}

	protected String autoSummary(final SocioPlanModel plan, final boolean portuguese)
	{
		final int wave = intOf(plan.getWave());
		final int discount = intOf(plan.getTicketDiscount());
		final List<String> parts = new ArrayList<>();
		if (wave > 0)
		{
			parts.add(portuguese ? "Prioridade " + wave : "Priority " + wave);
		}
		if (discount > 0)
		{
			parts.add(portuguese ? discount + "% de desconto na inteira" : discount + "% off the full ticket price");
		}
		if (parts.isEmpty())
		{
			parts.add(portuguese ? "Benefícios do programa " + StringUtils.defaultIfBlank(plan.getFamily(), "sócio-torcedor")
					: StringUtils.defaultIfBlank(plan.getFamily(), "Membership") + " benefits");
		}
		return String.join(" · ", parts);
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

	protected <T> T item(final String query)
	{
		final List<T> result = flexibleSearchService.<T> search(new FlexibleSearchQuery(query)).getResult();
		return result.isEmpty() ? null : result.get(0);
	}

	protected UnitModel unit(final String code)
	{
		return item("SELECT {pk} FROM {Unit} WHERE {code}='" + code + "'");
	}

	protected void removeAll(final Object items)
	{
		if (items instanceof Collection && !((Collection<?>) items).isEmpty())
		{
			modelService.removeAll(new ArrayList<>((Collection<?>) items));
		}
	}

	protected Map<Locale, String> localized(final String pt, final String en)
	{
		final Map<Locale, String> map = new LinkedHashMap<>();
		map.put(PT, pt);
		map.put(EN, en);
		return map;
	}

	protected void runImpex(final List<String> lines, final String code)
	{
		final ImportConfig config = new ImportConfig();
		config.setScript(new StreamBasedImpExResource(
				new ByteArrayInputStream(String.join("\n", lines).getBytes(StandardCharsets.UTF_8)), "UTF-8"));
		config.setSynchronous(true);
		config.setLegacyMode(Boolean.FALSE);
		final ImportResult result = importService.importData(config);
		if (!result.isSuccessful())
		{
			String detail = "";
			final ImpExMediaModel unresolved = result.getUnresolvedLines();
			if (unresolved != null)
			{
				detail = new String(mediaService.getDataFromMedia(unresolved), StandardCharsets.UTF_8);
			}
			LOG.error("Falha no ImpEx do plano {}:\n{}", code, detail);
			throw new IllegalStateException("Não foi possível gravar o plano " + code + ". "
					+ StringUtils.abbreviate(detail.replaceAll("\\s+", " "), 400));
		}
	}

	protected static String q(final String text)
	{
		return "\"" + StringUtils.defaultString(text).replace("\"", "\"\"") + "\"";
	}

	protected static String slug(final String text)
	{
		final String plain = Normalizer.normalize(StringUtils.defaultString(text), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		final String slug = plain.replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-+|-+$", "");
		return slug.isEmpty() ? "PLANO" : slug;
	}

	protected static String money(final double value)
	{
		final NumberFormat format = NumberFormat.getNumberInstance(new Locale("pt", "BR"));
		format.setMinimumFractionDigits(2);
		format.setMaximumFractionDigits(2);
		return "R$ " + format.format(value);
	}

	protected static int intOf(final Integer value)
	{
		return value == null ? 0 : value.intValue();
	}

	protected static double doubleOf(final Double value)
	{
		return value == null ? 0 : value.doubleValue();
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}

	public void setSessionService(final SessionService sessionService)
	{
		this.sessionService = sessionService;
	}

	public void setCatalogVersionService(final CatalogVersionService catalogVersionService)
	{
		this.catalogVersionService = catalogVersionService;
	}

	public void setCommonI18NService(final CommonI18NService commonI18NService)
	{
		this.commonI18NService = commonI18NService;
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

	public void setEnumerationService(final EnumerationService enumerationService)
	{
		this.enumerationService = enumerationService;
	}
}
