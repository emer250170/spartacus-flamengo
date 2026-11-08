package br.com.flamengo.matchday.mcp;

import br.com.flamengo.matchday.util.BackofficeRequestContext;
import de.hybris.platform.catalog.CatalogVersionService;
import de.hybris.platform.catalog.model.CatalogVersionModel;
import de.hybris.platform.category.model.CategoryModel;
import de.hybris.platform.core.Registry;
import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.enumeration.EnumerationService;
import de.hybris.platform.impex.model.ImpExMediaModel;
import de.hybris.platform.servicelayer.impex.ImportConfig;
import de.hybris.platform.servicelayer.impex.ImportResult;
import de.hybris.platform.servicelayer.impex.ImportService;
import de.hybris.platform.servicelayer.impex.impl.StreamBasedImpExResource;
import de.hybris.platform.servicelayer.media.MediaService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Cupom pessoal enviado a um cliente (tipo FlaCouponGrant), criado por um gestor pelo Claude (MCP):
 * <ol>
 * <li>{@link #validate}: confere área, escopo, desconto, teto e validade (chamado pelo interceptor, antes de gravar);</li>
 * <li>{@link #publishAndSend}: depois da gravação cria o cupom de uso único (SingleCodeCoupon) e a promoção só para
 * esse cliente, publica no módulo de promoções e envia o e-mail ao cliente com o código.</li>
 * </ol>
 * Escopo por área: loja (produtos da loja, Mengo Estúdio, personalização, experiências, produtos ou categorias
 * escolhidos), jogos (ingressos, ingressos de um jogo, estacionamento, alimentação) e sócio (planos e produtos da loja).
 * Cupom de ingresso não vale para quem é sócio (já tem o desconto do plano no ingresso).
 * <p>
 * Chaves: {@code flamengo.mcp.cupom.ativo}, {@code flamengo.mcp.cupom.tetoPercentual} (30),
 * {@code flamengo.mcp.cupom.tetoValor} (100), {@code flamengo.mcp.cupom.validadeDias} (30),
 * {@code flamengo.mcp.cupom.validadeMaxDias} (180).
 */
public class FlaCouponGrantService
{
	private static final Logger LOG = LoggerFactory.getLogger(FlaCouponGrantService.class);

	static final String TYPE = "FlaCouponGrant";
	static final String CHAVE_ATIVO = "flamengo.mcp.cupom.ativo";
	private static final String CATALOG = "flamengoProductCatalog";
	private static final String PROMOTION_GROUP = "electronicsPromoGrp";
	private static final String RULE_GROUP = "couponPromotionRuleGroup";
	private static final String RULE_MODULE = "promotions-module";
	private static final String FREE_DELIVERY_MODE = "frete-gratis";
	private static final Locale PT = new Locale("pt", "BR");
	private static final TimeZone BRASILIA = TimeZone.getTimeZone("America/Sao_Paulo");

	public static final String PERCENTUAL = "PERCENTUAL";
	public static final String VALOR = "VALOR";
	public static final String FRETE = "FRETE";

	/** Fora de "produtos da loja": o mesmo corte do desconto de sócio na loja (DefaultSocioPlanService) */
	private static final String[] STORE_EXCLUDED = { "flamengo_md_comidas", "flamengo_md_estacionamento", "flamengo_jogos_casa",
			"setor", "setor-norte", "setor-sul", "setor-leste-inf", "setor-oeste-sup", "socio_torcedor", "socio_torcedor_fora",
			"experiencias" };
	private static final String[] SECTORS = { "setor-norte", "setor-sul", "setor-leste-inf", "setor-oeste-sup" };
	/** Categorias que não entram em CATEGORIAS do gestor da loja (são de outra área) */
	private static final Set<String> NOT_STORE = new TreeSet<>(Arrays.asList(STORE_EXCLUDED));

	/** Escopos: área que pode usar, nome para o e-mail e categorias fixas (vazio = calculado) */
	private static final Map<String, String[]> SCOPES = new LinkedHashMap<>();
	static
	{
		SCOPES.put("LOJA", new String[] { "LOJA,SOCIO", "produtos da loja" });
		SCOPES.put("MENGO_ESTUDIO", new String[] { "LOJA", "Mengo Estúdio", "mengo_estudio" });
		SCOPES.put("PERSONALIZACAO", new String[] { "LOJA", "personalização de camisa", "flamengo_personalizacao", "flamengo_c149" });
		SCOPES.put("EXPERIENCIAS", new String[] { "LOJA", "experiências", "experiencias" });
		SCOPES.put("PRODUTOS", new String[] { "LOJA", "produtos escolhidos" });
		SCOPES.put("CATEGORIAS", new String[] { "LOJA", "categorias escolhidas" });
		SCOPES.put("INGRESSOS", new String[] { "JOGOS", "ingressos" });
		SCOPES.put("INGRESSOS_JOGO", new String[] { "JOGOS", "ingressos do jogo" });
		SCOPES.put("ESTACIONAMENTO", new String[] { "JOGOS", "estacionamento", "flamengo_md_estacionamento" });
		SCOPES.put("ALIMENTACAO", new String[] { "JOGOS", "alimentação no estádio", "flamengo_md_comidas" });
		SCOPES.put("PLANOS", new String[] { "SOCIO", "mensalidade do plano de sócio", "socio_torcedor" });
	}

	private McpSupport mcpSupport;
	private ModelService modelService;
	private FlexibleSearchService flexibleSearchService;
	private CatalogVersionService catalogVersionService;
	private ImportService importService;
	private MediaService mediaService;
	private EnumerationService enumerationService;

	// ------------------------------------------------------------ conferência (antes de gravar)

	/** Confere e completa o cupom novo; erro = IllegalArgumentException com a mensagem para o gestor */
	public void validate(final ItemModel grant)
	{
		if (!mcpSupport.flag(CHAVE_ATIVO, true))
		{
			throw new IllegalArgumentException("O cupom pessoal pelo Claude está desligado no servidor (" + CHAVE_ATIVO + ").");
		}
		final de.hybris.platform.core.model.user.UserModel user = mcpSupport.currentUser();
		final String area = mcpSupport.resolveArea(get(grant, "area"), user);

		final String code = StringUtils.upperCase(StringUtils.trimToEmpty(get(grant, "code")), Locale.ROOT);
		if (!code.matches("FLA[A-Z0-9]{6,12}"))
		{
			throw new IllegalArgumentException("Código de cupom inválido: use FLA + 6 a 12 letras ou números.");
		}
		if (one("SELECT {pk} FROM {AbstractCoupon} WHERE {couponId}=?code", "code", code) != null)
		{
			throw new IllegalArgumentException("Já existe um cupom " + code + ".");
		}
		final Object customer = modelService.getAttributeValue(grant, "customer");
		if (!(customer instanceof CustomerModel))
		{
			throw new IllegalArgumentException("Informe o cliente (e-mail da conta na loja).");
		}
		final CustomerModel client = (CustomerModel) customer;
		if (!McpSupport.validEmail(client.getUid()))
		{
			throw new IllegalArgumentException("O cliente " + client.getUid() + " não tem e-mail válido na conta.");
		}

		final String type = StringUtils.upperCase(StringUtils.defaultIfBlank(get(grant, "discountType"), PERCENTUAL), Locale.ROOT);
		final Double amountValue = (Double) modelService.getAttributeValue(grant, "amount");
		final double amount = amountValue == null ? 0 : amountValue.doubleValue();
		if (PERCENTUAL.equals(type))
		{
			final double cap = mcpSupport.number("flamengo.mcp.cupom.tetoPercentual", 30);
			if (amount <= 0 || amount > cap)
			{
				throw new IllegalArgumentException("Desconto percentual entre 1% e " + fmt(cap) + "% (teto do servidor).");
			}
		}
		else if (VALOR.equals(type))
		{
			final double cap = mcpSupport.number("flamengo.mcp.cupom.tetoValor", 100);
			if (amount <= 0 || amount > cap)
			{
				throw new IllegalArgumentException("Desconto em reais entre R$ 1 e " + money(cap) + " (teto do servidor).");
			}
		}
		else if (!FRETE.equals(type))
		{
			throw new IllegalArgumentException("Tipo de desconto: PERCENTUAL, VALOR ou FRETE.");
		}
		final Double minimum = (Double) modelService.getAttributeValue(grant, "minimum");
		if (minimum != null && minimum.doubleValue() < 0)
		{
			throw new IllegalArgumentException("Valor mínimo não pode ser negativo.");
		}

		final List<String> scopes = scopes(get(grant, "scope"));
		if (scopes.isEmpty())
		{
			throw new IllegalArgumentException("Informe onde o cupom vale (escopo).");
		}
		for (final String scope : scopes)
		{
			final String[] def = SCOPES.get(scope);
			if (def == null)
			{
				throw new IllegalArgumentException("Escopo desconhecido: " + scope + ". Use: " + String.join(", ", SCOPES.keySet()) + ".");
			}
			if (!Arrays.asList(def[0].split(",")).contains(area))
			{
				throw new IllegalArgumentException("O escopo " + scope + " (" + def[1] + ") não é da área " + area + ".");
			}
		}
		final boolean productScope = scopes.contains("PRODUTOS") || scopes.contains("INGRESSOS_JOGO");
		if (productScope && scopes.size() > 1)
		{
			throw new IllegalArgumentException("PRODUTOS e INGRESSOS_JOGO não se combinam com outros escopos: crie cupons separados.");
		}
		// confere os códigos agora (a promoção é montada com eles depois)
		final List<String> detail = codes(get(grant, "scopeDetail"));
		if (scopes.contains("PRODUTOS"))
		{
			productCodes(detail, false);
		}
		if (scopes.contains("INGRESSOS_JOGO"))
		{
			productCodes(detail, true);
		}
		if (scopes.contains("CATEGORIAS"))
		{
			storeCategories(detail);
		}
		if (touchesTickets(scopes))
		{
			final String plan = memberPlan(client);
			if (plan != null)
			{
				throw new IllegalArgumentException(client.getName() + " é sócio (" + plan
						+ ") e já tem o desconto do plano no ingresso: cupom de ingresso não acumula com ele.");
			}
		}

		Date until = (Date) modelService.getAttributeValue(grant, "validUntil");
		final Date now = new Date();
		if (until == null)
		{
			until = endOfDay(addDays(now, (int) mcpSupport.number("flamengo.mcp.cupom.validadeDias", 30)));
		}
		if (!until.after(now))
		{
			throw new IllegalArgumentException("A validade tem que ser uma data futura.");
		}
		if (until.after(addDays(now, (int) mcpSupport.number("flamengo.mcp.cupom.validadeMaxDias", 180) + 1)))
		{
			throw new IllegalArgumentException("Validade máxima: " + fmt(mcpSupport.number("flamengo.mcp.cupom.validadeMaxDias", 180)) + " dias.");
		}

		modelService.setAttributeValue(grant, "code", code);
		modelService.setAttributeValue(grant, "area", area);
		modelService.setAttributeValue(grant, "discountType", type);
		modelService.setAttributeValue(grant, "scope", String.join(",", scopes));
		modelService.setAttributeValue(grant, "scopeDetail", detail.isEmpty() ? null : String.join(",", detail));
		modelService.setAttributeValue(grant, "validUntil", until);
		modelService.setAttributeValue(grant, "requestedBy", user.getUid());
		modelService.setAttributeValue(grant, "ruleCode", ruleCode(code));
		modelService.setAttributeValue(grant, "status", "PUBLICANDO");
		modelService.setAttributeValue(grant, "message", "Criando o cupom e publicando a promoção (pode levar até 2 minutos).");
	}

	// ------------------------------------------------------------ depois da gravação

	/** Cria cupom e promoção, publica e envia o e-mail ao cliente; grava ENVIADO ou ERRO */
	public void publishAndSend(final ItemModel grant)
	{
		final String code = get(grant, "code");
		try
		{
			final CustomerModel customer = (CustomerModel) modelService.getAttributeValue(grant, "customer");
			final Date until = (Date) modelService.getAttributeValue(grant, "validUntil");
			final String description = describe(grant);
			createCoupon(code, customer, until, description);
			final String rule = ruleCode(code);
			createRule(grant, rule, customer, description);
			final String published = publish(rule);
			if (published != null)
			{
				mcpSupport.status(grant, "ERRO", "Cupom e promoção criados, mas a publicação falhou: " + published, false);
				return;
			}
			final boolean ok = mcpSupport.send(List.of(customer.getUid()), "Seu cupom " + code + " · Loja Flamengo",
					customerEmail(grant, customer, description), null, null);
			mcpSupport.status(grant, ok ? "ENVIADO" : "ERRO", ok
					? "Promoção publicada e e-mail enviado para " + customer.getUid() + ". " + description
					: "Promoção publicada, mas o servidor de e-mail recusou o envio para " + customer.getUid() + ".", ok);
		}
		catch (final RuntimeException e)
		{
			LOG.error("Falha no cupom pessoal " + code, e);
			mcpSupport.status(grant, "ERRO", "Falha: " + e.getMessage(), false);
		}
	}

	protected void createCoupon(final String code, final CustomerModel customer, final Date until, final String description)
	{
		final SimpleDateFormat date = impexDate();
		final List<String> lines = new ArrayList<>();
		lines.add("INSERT_UPDATE SingleCodeCoupon;couponId[unique=true];name[lang=pt];name[lang=en];active;"
				+ "startDate[dateformat=dd.MM.yyyy HH:mm:ss];endDate[dateformat=dd.MM.yyyy HH:mm:ss];maxRedemptionsPerCustomer;maxTotalRedemptions");
		lines.add(";" + code + ";" + q("Cupom pessoal " + customer.getUid() + ": " + description) + ";"
				+ q("Personal coupon " + customer.getUid()) + ";true;" + date.format(new Date(System.currentTimeMillis() - 60_000)) + ";"
				+ date.format(until) + ";1;1");
		runImpex(lines, code);
	}

	protected void createRule(final ItemModel grant, final String rule, final CustomerModel customer, final String description)
	{
		final String code = get(grant, "code");
		final String type = get(grant, "discountType");
		final List<String> scopes = scopes(get(grant, "scope"));
		final Double minimum = (Double) modelService.getAttributeValue(grant, "minimum");
		final Date until = (Date) modelService.getAttributeValue(grant, "validUntil");

		final List<String> conditions = new ArrayList<>();
		conditions.add("{\"definitionId\":\"y_qualifying_coupons\",\"parameters\":{\"coupons\":{\"type\":\"List(ItemType(AbstractCoupon))\","
				+ "\"value\":[\"" + code + "\"]}},\"children\":[]}");
		final List<String> excludedGroups = touchesTickets(scopes) ? planGroups() : new ArrayList<>();
		conditions.add("{\"definitionId\":\"y_target_customers\",\"parameters\":{"
				+ "\"customer_groups_operator\":{\"type\":\"Enum(de.hybris.platform.ruledefinitions.CollectionOperator)\",\"value\":\"CONTAINS_ANY\"},"
				+ "\"customer_groups\":{\"type\":\"List(ItemType(UserGroup))\",\"value\":[]},"
				+ "\"customers\":{\"type\":\"List(ItemType(Customer))\",\"value\":[\"" + customer.getUid().replace("\"", "") + "\"]},"
				+ "\"excluded_customer_groups\":{\"type\":\"List(ItemType(UserGroup))\",\"value\":" + json(excludedGroups) + "},"
				+ "\"excluded_customers\":{\"type\":\"List(ItemType(Customer))\",\"value\":[]}},\"children\":[]}");
		conditions.add(scopeCondition(scopes, codes(get(grant, "scopeDetail"))));
		if (minimum != null && minimum.doubleValue() > 0)
		{
			conditions.add("{\"definitionId\":\"y_cart_total\",\"parameters\":{\"value\":{\"type\":\"Map(ItemType(Currency),java.math.BigDecimal)\","
					+ "\"value\":{\"BRL\":" + plain(minimum) + "}},\"operator\":{\"type\":\"Enum(de.hybris.platform.ruledefinitions.AmountOperator)\","
					+ "\"value\":\"GREATER_THAN_OR_EQUAL\"}},\"children\":[]}");
		}
		final Double amount = (Double) modelService.getAttributeValue(grant, "amount");
		final String action;
		if (PERCENTUAL.equals(type))
		{
			action = "{\"definitionId\":\"y_order_entry_percentage_discount\",\"parameters\":{\"value\":{\"type\":\"java.math.BigDecimal\",\"value\":"
					+ plain(amount) + "}}}";
		}
		else if (VALOR.equals(type))
		{
			action = "{\"definitionId\":\"y_order_fixed_discount\",\"parameters\":{\"value\":{\"type\":\"Map(ItemType(Currency),java.math.BigDecimal)\","
					+ "\"value\":{\"BRL\":" + plain(amount) + "}}}}";
		}
		else
		{
			action = "{\"definitionId\":\"y_change_delivery_mode\",\"parameters\":{\"delivery_mode\":{\"type\":\"ItemType(DeliveryMode)\",\"value\":\""
					+ FREE_DELIVERY_MODE + "\"}}}";
		}
		// percentual nos itens: acima do desconto de sócio na loja (220) e dos cupons da loja, para os dois valerem
		final int priority = PERCENTUAL.equals(type) ? 240 : 160;
		final SimpleDateFormat date = impexDate();
		final List<String> lines = new ArrayList<>();
		lines.add("INSERT_UPDATE PromotionSourceRule;code[unique=true];version[unique=true];name[lang=pt];name[lang=en];description[lang=pt];"
				+ "messageFired[lang=pt];messageFired[lang=en];priority;stackable;maxAllowedRuns;website(Identifier);ruleGroup(code);"
				+ "startDate[dateformat=dd.MM.yyyy HH:mm:ss];endDate[dateformat=dd.MM.yyyy HH:mm:ss];conditions;actions");
		lines.add(";" + rule + ";0;" + q("Cupom pessoal " + code + ": " + description) + ";" + q("Personal coupon " + code) + ";"
				+ q("Cupom pessoal de " + customer.getUid() + " criado por " + get(grant, "requestedBy") + " pelo Claude (MCP): " + description) + ";"
				+ q("Cupom " + code + ": " + description) + ";" + q("Coupon " + code) + ";" + priority + ";true;1;" + PROMOTION_GROUP + ";"
				+ RULE_GROUP + ";" + impexDate().format(new Date(System.currentTimeMillis() - 60_000)) + ";" + date.format(until) + ";"
				+ q("[" + String.join(",", conditions) + "]") + ";" + q("[" + action + "]"));
		runImpex(lines, code);
	}

	/** Condição de escopo: categorias (uma ou mais) ou produtos (PRODUTOS / INGRESSOS_JOGO) */
	protected String scopeCondition(final List<String> scopes, final List<String> detail)
	{
		if (scopes.contains("PRODUTOS") || scopes.contains("INGRESSOS_JOGO"))
		{
			final List<String> products = productCodes(detail, scopes.contains("INGRESSOS_JOGO"));
			return "{\"definitionId\":\"y_qualifying_products\",\"parameters\":{"
					+ "\"quantity\":{\"type\":\"java.lang.Integer\",\"value\":1},"
					+ "\"operator\":{\"type\":\"Enum(de.hybris.platform.ruledefinitions.AmountOperator)\",\"value\":\"GREATER_THAN_OR_EQUAL\"},"
					+ "\"products_operator\":{\"type\":\"Enum(de.hybris.platform.ruledefinitions.CollectionOperator)\",\"value\":\"CONTAINS_ANY\"},"
					+ "\"products\":{\"type\":\"List(ItemType(Product))\",\"value\":" + json(products) + "}},\"children\":[]}";
		}
		final Set<String> categories = new TreeSet<>();
		final Set<String> explicit = new TreeSet<>();
		for (final String scope : scopes)
		{
			switch (scope)
			{
				case "LOJA":
					categories.addAll(storeRoots());
					break;
				case "INGRESSOS":
					explicit.addAll(Arrays.asList(SECTORS));
					break;
				case "CATEGORIAS":
					explicit.addAll(storeCategories(detail));
					break;
				default:
					final String[] def = SCOPES.get(scope);
					explicit.addAll(Arrays.asList(def).subList(2, def.length));
			}
		}
		categories.addAll(explicit);
		// "produtos da loja" tira jogos, sócio, comida, estacionamento e experiências, menos o que foi pedido junto
		final Set<String> excluded = new TreeSet<>();
		if (scopes.contains("LOJA"))
		{
			excluded.addAll(Arrays.asList(STORE_EXCLUDED));
			excluded.removeAll(explicit);
		}
		return "{\"definitionId\":\"y_qualifying_categories\",\"parameters\":{"
				+ "\"quantity\":{\"type\":\"java.lang.Integer\",\"value\":1},"
				+ "\"operator\":{\"type\":\"Enum(de.hybris.platform.ruledefinitions.AmountOperator)\",\"value\":\"GREATER_THAN_OR_EQUAL\"},"
				+ "\"categories_operator\":{\"type\":\"Enum(de.hybris.platform.ruledefinitions.CollectionOperator)\",\"value\":\"CONTAINS_ANY\"},"
				+ "\"categories\":{\"type\":\"List(ItemType(Category))\",\"value\":" + json(categories) + "},"
				+ "\"excluded_categories\":{\"type\":\"List(ItemType(Category))\",\"value\":" + json(excluded) + "},"
				+ "\"excluded_products\":{\"type\":\"List(ItemType(Product))\",\"value\":[]}},\"children\":[]}";
	}

	/** Publica a promoção (espera terminar); null = ok, senão o motivo */
	protected String publish(final String code)
	{
		final ItemModel rule = one("SELECT {pk} FROM {PromotionSourceRule} WHERE {code}=?code ORDER BY {version} DESC", "code", code);
		if (rule == null)
		{
			return "promoção " + code + " não encontrada";
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
				return "serviço de publicação indisponível";
			}
			final Object result = compile.invoke(maintenance, List.of(rule), RULE_MODULE, Boolean.TRUE);
			modelService.refresh(rule);
			final String text = String.valueOf(result);
			LOG.info("Cupom pessoal: promoção {} publicada: {}", code, text);
			if (text.contains("ERROR") || text.contains("FAILED"))
			{
				return StringUtils.abbreviate(text, 500);
			}
			modelService.setAttributeValue(rule, "status", (Object) enumerationService.getEnumerationValue("RuleStatus", "PUBLISHED"));
			modelService.save(rule);
			return null;
		}
		catch (final Exception e)
		{
			LOG.error("Falha ao publicar a promoção " + code, e);
			return e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
		}
	}

	// ------------------------------------------------------------ e-mail ao cliente

	protected String customerEmail(final ItemModel grant, final CustomerModel customer, final String description)
	{
		final String code = get(grant, "code");
		final Date until = (Date) modelService.getAttributeValue(grant, "validUntil");
		final String first = StringUtils.substringBefore(StringUtils.defaultIfBlank(customer.getName(), "torcedor"), " ");
		final String note = StringUtils.trimToNull(get(grant, "note"));
		final StringBuilder html = new StringBuilder();
		html.append("<p>Olá, ").append(McpSupport.esc(first)).append("!</p>");
		if (note != null)
		{
			html.append("<p>").append(McpSupport.esc(note).replace("\n", "<br>")).append("</p>");
		}
		else
		{
			html.append("<p>Separamos um cupom de desconto só para você na Loja Flamengo.</p>");
		}
		html.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:16px 0;border:2px dashed #c4161c;border-radius:6px\">")
				.append("<tr><td style=\"padding:14px 22px;text-align:center\">")
				.append("<div style=\"font-size:12px;color:#666;text-transform:uppercase;letter-spacing:1px\">Seu cupom</div>")
				.append("<div style=\"font-size:26px;font-weight:bold;letter-spacing:3px;color:#c4161c;margin:4px 0\">")
				.append(McpSupport.esc(code)).append("</div>")
				.append("<div style=\"font-size:15px;font-weight:bold\">").append(McpSupport.esc(description)).append("</div>")
				.append("</td></tr></table>");
		html.append("<ul style=\"padding-left:18px;margin:0 0 16px 0\">")
				.append("<li>Válido até ").append(McpSupport.day(until)).append(".</li>")
				.append("<li>Uso único, só na sua conta (").append(McpSupport.esc(customer.getUid())).append(").</li>")
				.append("<li>No carrinho, informe o código em \"Cupom de desconto\".</li>")
				.append("</ul>")
				.append("<p><a href=\"").append(mcpSupport.storeUrl()).append("\" style=\"display:inline-block;background:#c4161c;color:#ffffff;")
				.append("text-decoration:none;padding:10px 18px;border-radius:4px;font-weight:bold\">Ir para a loja</a></p>");
		return mcpSupport.frame("Um cupom para você", html.toString(),
				"Loja Flamengo · cupom pessoal e intransferível. Se você não reconhece este e-mail, ignore-o.");
	}

	/** "15% em produtos da loja e Mengo Estúdio, em compras a partir de R$ 200,00" */
	public String describe(final ItemModel grant)
	{
		final String type = get(grant, "discountType");
		final Double amount = (Double) modelService.getAttributeValue(grant, "amount");
		final Double minimum = (Double) modelService.getAttributeValue(grant, "minimum");
		final List<String> names = new ArrayList<>();
		for (final String scope : scopes(get(grant, "scope")))
		{
			final String[] def = SCOPES.get(scope);
			String name = def == null ? scope : def[1];
			if ("INGRESSOS_JOGO".equals(scope) || "PRODUTOS".equals(scope) || "CATEGORIAS".equals(scope))
			{
				name = name + " (" + String.join(", ", codes(get(grant, "scopeDetail"))) + ")";
			}
			names.add(name);
		}
		final String where = names.size() <= 1 ? String.join("", names)
				: String.join(", ", names.subList(0, names.size() - 1)) + " e " + names.get(names.size() - 1);
		final String what;
		if (PERCENTUAL.equals(type))
		{
			what = fmt(amount) + "% de desconto em " + where;
		}
		else if (VALOR.equals(type))
		{
			what = money(amount) + " de desconto na compra com " + where;
		}
		else
		{
			what = "Frete grátis na compra com " + where;
		}
		return what + (minimum != null && minimum.doubleValue() > 0 ? ", em compras a partir de " + money(minimum) : "");
	}

	// ------------------------------------------------------------ apoio

	protected static boolean touchesTickets(final List<String> scopes)
	{
		return scopes.contains("INGRESSOS") || scopes.contains("INGRESSOS_JOGO");
	}

	/** Plano de sócio do cliente (nome), ou null */
	protected String memberPlan(final CustomerModel customer)
	{
		final List<ItemModel> plans = flexibleSearchService.<ItemModel> search(new FlexibleSearchQuery("SELECT {pk} FROM {SocioPlan}")).getResult();
		for (final ItemModel plan : plans)
		{
			final Object group = modelService.getAttributeValue(plan, "customerGroup");
			if (group instanceof de.hybris.platform.core.model.user.UserGroupModel
					&& mcpSupport.getUserService().isMemberOfGroup(customer, (de.hybris.platform.core.model.user.UserGroupModel) group))
			{
				final String name = modelService.getAttributeValue(plan, "name", new Locale("pt"));
				return StringUtils.defaultIfBlank(name, (String) modelService.getAttributeValue(plan, "code"));
			}
		}
		return null;
	}

	protected List<String> planGroups()
	{
		final Set<String> groups = new TreeSet<>();
		for (final ItemModel plan : flexibleSearchService.<ItemModel> search(new FlexibleSearchQuery("SELECT {pk} FROM {SocioPlan}")).getResult())
		{
			final Object group = modelService.getAttributeValue(plan, "customerGroup");
			if (group instanceof de.hybris.platform.core.model.user.UserGroupModel)
			{
				groups.add(((de.hybris.platform.core.model.user.UserGroupModel) group).getUid());
			}
		}
		return new ArrayList<>(groups);
	}

	protected CatalogVersionModel online()
	{
		return catalogVersionService.getCatalogVersion(CATALOG, "Online");
	}

	/** Categorias de topo do catálogo Online (como no desconto de sócio na loja) */
	protected Set<String> storeRoots()
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery("SELECT {pk} FROM {Category} WHERE {catalogVersion}=?cv");
		query.addQueryParameter("cv", online());
		final Set<String> roots = new TreeSet<>();
		for (final CategoryModel category : flexibleSearchService.<CategoryModel> search(query).getResult())
		{
			if (category.getSupercategories() == null || category.getSupercategories().isEmpty())
			{
				roots.add(category.getCode());
			}
		}
		return roots;
	}

	/** Categorias escolhidas pelo gestor da loja: existem no Online e não são de jogos, sócio, comida ou estacionamento */
	protected List<String> storeCategories(final List<String> codes)
	{
		if (codes.isEmpty())
		{
			throw new IllegalArgumentException("Informe os códigos das categorias (scopeDetail).");
		}
		for (final String code : codes)
		{
			if (NOT_STORE.contains(code) && !"experiencias".equals(code))
			{
				throw new IllegalArgumentException("A categoria " + code + " não é da loja (é de jogos, sócio, comida ou estacionamento).");
			}
			final FlexibleSearchQuery query = new FlexibleSearchQuery("SELECT {pk} FROM {Category} WHERE {code}=?code AND {catalogVersion}=?cv");
			query.addQueryParameter("code", code);
			query.addQueryParameter("cv", online());
			if (flexibleSearchService.search(query).getResult().isEmpty())
			{
				throw new IllegalArgumentException("Categoria " + code + " não encontrada no catálogo da loja.");
			}
		}
		return codes;
	}

	/**
	 * Produtos do escopo. tickets=false: produtos da loja (não ingresso nem plano), com as variantes (tamanhos).
	 * tickets=true: jogos (JOGO-AAAAMMDD-ADV), com todas as poltronas.
	 */
	protected List<String> productCodes(final List<String> codes, final boolean tickets)
	{
		if (codes.isEmpty())
		{
			throw new IllegalArgumentException(tickets ? "Informe o código do jogo (ex.: JOGO-20261115-BAH)." : "Informe os códigos dos produtos.");
		}
		final Set<String> out = new LinkedHashSet<>();
		for (final String code : codes)
		{
			final boolean isTicket = code.startsWith("JOGO-");
			if (tickets != isTicket || code.startsWith("NACAO-"))
			{
				throw new IllegalArgumentException(tickets ? code + " não é um jogo (use JOGO-AAAAMMDD-ADV)."
						: code + " é ingresso ou plano de sócio: não entra em cupom da loja.");
			}
			final FlexibleSearchQuery query = new FlexibleSearchQuery("SELECT {pk} FROM {Product} WHERE {code}=?code AND {catalogVersion}=?cv");
			query.addQueryParameter("code", code);
			query.addQueryParameter("cv", online());
			final List<ProductModel> found = flexibleSearchService.<ProductModel> search(query).getResult();
			if (found.isEmpty())
			{
				throw new IllegalArgumentException("Produto " + code + " não encontrado no catálogo da loja.");
			}
			out.add(code);
			addVariants(found.get(0), out);
		}
		return new ArrayList<>(out);
	}

	protected void addVariants(final ProductModel product, final Set<String> out)
	{
		final Collection<de.hybris.platform.variants.model.VariantProductModel> variants = product.getVariants();
		if (variants == null)
		{
			return;
		}
		for (final de.hybris.platform.variants.model.VariantProductModel variant : variants)
		{
			out.add(variant.getCode());
			addVariants(variant, out);
		}
	}

	protected static List<String> scopes(final String text)
	{
		final Set<String> out = new LinkedHashSet<>();
		for (final String part : StringUtils.defaultString(text).split("[,;\\s]+"))
		{
			if (!part.isBlank())
			{
				out.add(part.trim().toUpperCase(Locale.ROOT));
			}
		}
		return new ArrayList<>(out);
	}

	protected static List<String> codes(final String text)
	{
		final Set<String> out = new LinkedHashSet<>();
		for (final String part : StringUtils.defaultString(text).split("[,;\\s]+"))
		{
			if (!part.isBlank())
			{
				out.add(part.trim());
			}
		}
		return new ArrayList<>(out);
	}

	protected static String ruleCode(final String code)
	{
		return "cupom_pessoal_" + code.toLowerCase(Locale.ROOT);
	}

	protected ItemModel one(final String query, final String param, final Object value)
	{
		final FlexibleSearchQuery fsq = new FlexibleSearchQuery(query);
		fsq.addQueryParameter(param, value);
		final List<ItemModel> found = flexibleSearchService.<ItemModel> search(fsq).getResult();
		return found.isEmpty() ? null : found.get(0);
	}

	protected String get(final ItemModel item, final String attribute)
	{
		final Object value = modelService.getAttributeValue(item, attribute);
		return value == null ? null : value.toString();
	}

	protected void runImpex(final List<String> lines, final String code)
	{
		final ImportConfig config = new ImportConfig();
		config.setScript(new StreamBasedImpExResource(new ByteArrayInputStream(String.join("\n", lines).getBytes(StandardCharsets.UTF_8)), "UTF-8"));
		config.setSynchronous(true);
		config.setLegacyMode(Boolean.FALSE);
		final ImportResult result;
		final Runnable restore = BackofficeRequestContext.capture();
		try
		{
			result = importService.importData(config);
		}
		finally
		{
			restore.run();
		}
		if (!result.isSuccessful())
		{
			String detail = "";
			final ImpExMediaModel unresolved = result.getUnresolvedLines();
			if (unresolved != null)
			{
				detail = new String(mediaService.getDataFromMedia(unresolved), StandardCharsets.UTF_8);
			}
			LOG.error("Falha no ImpEx do cupom {}:\n{}", code, detail);
			throw new IllegalStateException("não foi possível gravar o cupom " + code + ": " + StringUtils.abbreviate(detail.replaceAll("\\s+", " "), 400));
		}
	}

	protected static SimpleDateFormat impexDate()
	{
		final SimpleDateFormat format = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.ROOT);
		format.setTimeZone(TimeZone.getDefault());
		return format;
	}

	protected static Date addDays(final Date date, final int days)
	{
		final Calendar calendar = Calendar.getInstance(BRASILIA);
		calendar.setTime(date);
		calendar.add(Calendar.DAY_OF_MONTH, days);
		return calendar.getTime();
	}

	protected static Date endOfDay(final Date date)
	{
		final Calendar calendar = Calendar.getInstance(BRASILIA);
		calendar.setTime(date);
		calendar.set(Calendar.HOUR_OF_DAY, 23);
		calendar.set(Calendar.MINUTE, 59);
		calendar.set(Calendar.SECOND, 59);
		calendar.set(Calendar.MILLISECOND, 0);
		return calendar.getTime();
	}

	protected static String q(final String text)
	{
		return "\"" + StringUtils.defaultString(text).replace("\"", "\"\"") + "\"";
	}

	protected static String json(final Collection<String> values)
	{
		final StringBuilder json = new StringBuilder("[");
		for (final String value : values)
		{
			json.append(json.length() > 1 ? "," : "").append('"').append(value.replace("\"", "")).append('"');
		}
		return json.append(']').toString();
	}

	protected static String plain(final Double value)
	{
		return BigDecimal.valueOf(value == null ? 0 : value.doubleValue()).stripTrailingZeros().toPlainString();
	}

	protected static String fmt(final Double value)
	{
		return plain(value).replace('.', ',');
	}

	protected static String fmt(final double value)
	{
		return fmt(Double.valueOf(value));
	}

	protected static String money(final Double value)
	{
		final NumberFormat format = NumberFormat.getCurrencyInstance(PT);
		return format.format(value == null ? 0 : value.doubleValue()).replace(' ', ' ');
	}

	protected static String money(final double value)
	{
		return money(Double.valueOf(value));
	}

	// ------------------------------------------------------------ injeção

	public void setMcpSupport(final McpSupport mcpSupport)
	{
		this.mcpSupport = mcpSupport;
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

	public void setImportService(final ImportService importService)
	{
		this.importService = importService;
	}

	public void setMediaService(final MediaService mediaService)
	{
		this.mediaService = mediaService;
	}

	public void setEnumerationService(final EnumerationService enumerationService)
	{
		this.enumerationService = enumerationService;
	}
}
