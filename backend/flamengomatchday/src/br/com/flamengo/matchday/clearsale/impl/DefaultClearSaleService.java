package br.com.flamengo.matchday.clearsale.impl;

import br.com.flamengo.matchday.clearsale.ClearSaleClient;
import br.com.flamengo.matchday.clearsale.ClearSaleService;
import br.com.flamengo.matchday.customer.Telefone;
import br.com.flamengo.matchday.enums.SocioPaymentMethod;
import br.com.flamengo.matchday.service.SeatHoldService;
import br.com.flamengo.matchday.socio.SocioExperienceService;
import br.com.flamengo.matchday.socio.SocioPayments;
import br.com.flamengo.matchday.socio.SocioPlanService;
import br.com.flamengo.matchday.ticket.MatchdayTicketService;
import de.hybris.platform.commerceservices.event.OrderCancelledEvent;
import de.hybris.platform.commerceservices.model.PickUpDeliveryModeModel;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.order.payment.CreditCardPaymentInfoModel;
import de.hybris.platform.core.model.user.AddressModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.orderprocessing.model.OrderProcessModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.event.EventService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DefaultClearSaleService implements ClearSaleService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultClearSaleService.class);

	/** Chave geral (padrão true): false desliga a análise mesmo com o campo da loja base ligado */
	public static final String PROP_ATIVO = "flamengo.clearsale.ativo";
	public static final String PROP_TIMEOUT = "flamengo.clearsale.timeoutMs";
	public static final String PROP_RECUSA = "flamengo.clearsale.recusa";
	public static final String PROP_BIN_TESTE = "flamengo.clearsale.binTeste";
	public static final String INDISPONIVEL = "INDISPONIVEL";
	private static final String URL_PADRAO = "https://homologacao.clearsale.com.br/api";
	private static final String AUTOR = "ClearSale";

	private ClearSaleClient clearSaleClient;
	private ModelService modelService;
	private ConfigurationService configurationService;
	private BaseStoreService baseStoreService;
	private FlexibleSearchService flexibleSearchService;
	private EventService eventService;
	private SeatHoldService seatHoldService;
	private MatchdayTicketService matchdayTicketService;
	private SocioExperienceService socioExperienceService;
	private SocioPlanService socioPlanService;

	/** token por endereço + usuário, com a validade */
	private final Map<String, Object[]> tokens = new ConcurrentHashMap<>();

	@Override
	public String analisar(final OrderProcessModel process)
	{
		final OrderModel order = process == null ? null : process.getOrder();
		if (order == null)
		{
			return OK;
		}
		final BaseStoreModel store = order.getStore();
		if (!ligado(store))
		{
			return OK;
		}
		final String email = order.getUser() == null ? "" : StringUtils.defaultString(order.getUser().getUid()).toLowerCase();
		final Set<String> so = lista(str(store, "clearSaleEmails"));
		if (!so.isEmpty() && !so.contains(email))
		{
			return OK; // fora da lista de e-mails analisados
		}
		final Map<String, Object> pedido;
		try
		{
			pedido = montar(order);
		}
		catch (final IllegalStateException e)
		{
			registrar(order, INDISPONIVEL, null, null, "Sem análise: " + e.getMessage() + " O pedido seguiu.");
			return OK;
		}
		final String url = StringUtils.removeEnd(StringUtils.defaultIfBlank(str(store, "clearSaleUrl"), URL_PADRAO), "/");
		final int timeout = configurationService.getConfiguration().getInt(PROP_TIMEOUT, 8000);
		ClearSaleClient.Resposta r;
		try
		{
			final String token = token(url, str(store, "clearSaleUsuario"), str(store, "clearSaleSenha"), timeout, false);
			r = clearSaleClient.post(url + "/v1/orders", token, pedido, timeout);
			if (r.status == 401)
			{
				r = clearSaleClient.post(url + "/v1/orders", token(url, str(store, "clearSaleUsuario"), str(store, "clearSaleSenha"),
						timeout, true), pedido, timeout);
			}
		}
		catch (final IOException | RuntimeException e)
		{
			LOG.warn("Pedido {}: ClearSale sem resposta ({})", order.getCode(), e.getMessage());
			registrar(order, INDISPONIVEL, null, null, "ClearSale sem resposta (" + e.getMessage() + "). O pedido seguiu.");
			return OK;
		}
		if (!r.ok())
		{
			LOG.warn("Pedido {}: ClearSale recusou o envio (HTTP {}): {}", order.getCode(), r.status, StringUtils.left(r.texto, 500));
			registrar(order, INDISPONIVEL, null, r.requestId,
					"ClearSale devolveu HTTP " + r.status + ": " + StringUtils.left(r.texto, 400) + " O pedido seguiu.");
			return OK;
		}
		final Map<?, ?> resultado = primeiro(r.corpo.get("orders"));
		final String status = resultado == null ? null : StringUtils.trimToNull(String.valueOf(resultado.get("status")));
		final Double score = resultado == null ? null : numero(resultado.get("score"));
		if (status == null || "null".equals(status))
		{
			registrar(order, INDISPONIVEL, null, r.requestId, "ClearSale respondeu sem decisão. O pedido seguiu.");
			return OK;
		}
		final boolean recusado = lista(configurationService.getConfiguration().getString(PROP_RECUSA, "RPA,RPP,RPM,SUS,FRD"))
				.contains(status.toLowerCase());
		LOG.info("Pedido {}: ClearSale {} (score {}, Request-ID {})", order.getCode(), status, score, r.requestId);
		if (!recusado)
		{
			registrar(order, status, score, r.requestId, "Aprovado pelo ClearSale (" + status + "). O pedido seguiu.");
			return OK;
		}
		registrar(order, status, score, r.requestId, "Recusado pelo ClearSale (" + status + "): " + recusar(order, process, status));
		return RECUSADO;
	}

	/** Cancela sem cobrança e desfaz a venda; devolve o resumo do que foi feito */
	protected String recusar(final OrderModel order, final OrderProcessModel process, final String status)
	{
		final String motivo = "Recusado pela análise de fraude (ClearSale " + status + ").";
		final List<String> feito = new ArrayList<>();
		order.setStatus(OrderStatus.CANCELLED);
		modelService.save(order);
		feito.add("pedido cancelado sem cobrança");
		try
		{
			seatHoldService.revertSale(order);
			feito.add("estoque devolvido");
		}
		catch (final RuntimeException e)
		{
			LOG.error("Pedido " + order.getCode() + ": estoque não devolvido na recusa do ClearSale", e);
			feito.add("estoque NÃO devolvido (" + e.getMessage() + ")");
		}
		try
		{
			final int n = matchdayTicketService.cancelForOrder(order, motivo, AUTOR);
			if (n > 0)
			{
				feito.add(n + " ingresso(s) cancelado(s), poltrona(s) liberada(s)");
			}
		}
		catch (final RuntimeException e)
		{
			LOG.error("Pedido " + order.getCode() + ": ingressos não cancelados na recusa do ClearSale", e);
		}
		try
		{
			final FlexibleSearchQuery q = new FlexibleSearchQuery(
					"SELECT {voucher} FROM {SocioRedemption} WHERE {orderCode}=?o AND {cancelledAt} IS NULL");
			q.addQueryParameter("o", order.getCode());
			q.setResultClassList(Collections.singletonList(String.class));
			int n = 0;
			for (final String voucher : flexibleSearchService.<String> search(q).getResult())
			{
				socioExperienceService.cancelVoucher(voucher, AUTOR, motivo);
				n++;
			}
			if (n > 0)
			{
				feito.add(n + " voucher(s) de experiência cancelado(s)");
			}
		}
		catch (final RuntimeException e)
		{
			LOG.error("Pedido " + order.getCode() + ": vouchers não cancelados na recusa do ClearSale", e);
		}
		try
		{
			eventService.publishEvent(new OrderCancelledEvent(process));
			feito.add("e-mail de pedido cancelado enviado");
		}
		catch (final RuntimeException e)
		{
			LOG.error("Pedido " + order.getCode() + ": e-mail de cancelamento não disparado", e);
		}
		return String.join("; ", feito) + ".";
	}

	/** Corpo do POST /v1/orders (manual Realtime Decision) */
	protected Map<String, Object> montar(final OrderModel order)
	{
		if (!(order.getUser() instanceof CustomerModel))
		{
			throw new IllegalStateException("pedido sem cliente.");
		}
		final CustomerModel cliente = (CustomerModel) order.getUser();
		final String cpf = digits(attr(cliente, "cpf"));
		if (cpf.length() != 11)
		{
			throw new IllegalStateException("cliente sem CPF no cadastro.");
		}
		final List<Map<String, Object>> telefones = telefones(cliente, order);
		if (telefones.isEmpty())
		{
			throw new IllegalStateException("cliente sem telefone.");
		}
		final String nome = StringUtils.defaultIfBlank(cliente.getName(), cliente.getUid());
		final Map<String, Object> p = new LinkedHashMap<>();
		p.put("code", order.getCode());
		p.put("sessionID", StringUtils.left(StringUtils.defaultIfBlank(order.getGuid(), order.getCode()), 128));
		p.put("date", data(order.getDate()));
		p.put("email", cliente.getUid());
		p.put("b2bB2c", "B2C");
		p.put("itemValue", dinheiro(order.getSubtotal()));
		p.put("totalValue", dinheiro(order.getTotalPrice()));
		p.put("numberOfInstallments", parcelas(order));
		p.put("origin", "Loja Flamengo");
		p.put("country", "Brasil");

		final Map<String, Object> billing = new LinkedHashMap<>();
		billing.put("clientID", cliente.getUid());
		billing.put("type", Integer.valueOf(1));
		billing.put("primaryDocument", cpf);
		billing.put("name", StringUtils.left(nome, 500));
		billing.put("email", cliente.getUid());
		final Map<String, Object> endCobranca = endereco(order.getPaymentAddress() != null ? order.getPaymentAddress()
				: order.getDeliveryAddress());
		if (endCobranca != null)
		{
			billing.put("address", endCobranca);
		}
		billing.put("phones", telefones);
		p.put("billing", billing);

		final Map<String, Object> shipping = new LinkedHashMap<>();
		shipping.put("clientID", cliente.getUid());
		shipping.put("type", Integer.valueOf(1));
		shipping.put("primaryDocument", cpf);
		shipping.put("name", StringUtils.left(nome, 500));
		shipping.put("email", cliente.getUid());
		final boolean retirada = order.getDeliveryMode() instanceof PickUpDeliveryModeModel || order.getDeliveryMode() != null
				&& StringUtils.startsWith(order.getDeliveryMode().getCode(), "matchday");
		final Map<String, Object> endEntrega = retirada ? null : endereco(order.getDeliveryAddress());
		if (endEntrega != null)
		{
			shipping.put("address", endEntrega);
		}
		shipping.put("phones", telefones);
		if (order.getDeliveryMode() != null)
		{
			shipping.put("deliveryType", StringUtils.left(
					StringUtils.defaultIfBlank(order.getDeliveryMode().getName(), order.getDeliveryMode().getCode()), 100));
		}
		shipping.put("price", dinheiro(order.getDeliveryCost()));
		p.put("shipping", shipping);

		final Map<String, Object> pagamento = new LinkedHashMap<>();
		pagamento.put("sequential", Integer.valueOf(1));
		pagamento.put("date", data(order.getDate()));
		pagamento.put("value", dinheiro(order.getTotalPrice()));
		final int tipo = tipoPagamento(order);
		pagamento.put("type", Integer.valueOf(tipo));
		pagamento.put("installments", parcelas(order));
		if (tipo == 1)
		{
			pagamento.put("card", cartao(order, nome));
		}
		p.put("payments", Collections.singletonList(pagamento));

		final List<Map<String, Object>> itens = new ArrayList<>();
		if (order.getEntries() != null)
		{
			for (final AbstractOrderEntryModel e : order.getEntries())
			{
				if (e.getProduct() == null || e.getQuantity() == null || e.getQuantity().longValue() <= 0)
				{
					continue;
				}
				final Map<String, Object> item = new LinkedHashMap<>();
				item.put("code", StringUtils.left(e.getProduct().getCode(), 50));
				item.put("name", StringUtils.left(StringUtils.defaultIfBlank(e.getProduct().getName(), e.getProduct().getCode()), 150));
				item.put("value", dinheiro(e.getBasePrice()));
				item.put("amount", Integer.valueOf(e.getQuantity().intValue()));
				item.put("categoryName", categoria(e));
				itens.add(item);
			}
		}
		p.put("items", itens);
		return p;
	}

	protected String categoria(final AbstractOrderEntryModel e)
	{
		final String code = StringUtils.defaultString(e.getProduct().getCode());
		if (code.startsWith("JOGO-"))
		{
			return "Ingresso";
		}
		if (code.startsWith("FLA-EST-") || code.startsWith("FLA-FOOD-"))
		{
			return "Dia de jogo";
		}
		try
		{
			if (socioPlanService.findForProduct(e.getProduct()) != null)
			{
				return "Sócio-torcedor";
			}
		}
		catch (final RuntimeException ex)
		{
			// sem cadastro de planos: segue como loja
		}
		return "Loja";
	}

	/** 27 = Pix (e Pix Automático), 2 = boleto, 1 = cartão, 14 = outro */
	protected int tipoPagamento(final OrderModel order)
	{
		final SocioPaymentMethod m = SocioPayments.method(modelService, order);
		if (m == SocioPaymentMethod.PIX || m == SocioPaymentMethod.PIX_AUTOMATICO)
		{
			return 27;
		}
		if (m == SocioPaymentMethod.BOLETO)
		{
			return 2;
		}
		if (m == SocioPaymentMethod.CARTAO_MENSAL || m == SocioPaymentMethod.CARTAO_ANUAL
				|| order.getPaymentInfo() instanceof CreditCardPaymentInfoModel)
		{
			return 1;
		}
		return 14;
	}

	/** bin = 6 primeiros, end = 4 últimos. A demo guarda o número mascarado: sem os 6 primeiros, o bin de teste */
	protected Map<String, Object> cartao(final OrderModel order, final String nome)
	{
		String numero = "";
		String titular = nome;
		if (order.getPaymentInfo() instanceof CreditCardPaymentInfoModel)
		{
			final CreditCardPaymentInfoModel cc = (CreditCardPaymentInfoModel) order.getPaymentInfo();
			numero = StringUtils.defaultString(cc.getNumber());
			titular = StringUtils.defaultIfBlank(cc.getCcOwner(), nome);
		}
		final String d = digits(numero);
		final String bin = numero.length() >= 6 && numero.substring(0, 6).matches("\\d{6}") ? numero.substring(0, 6)
				: configurationService.getConfiguration().getString(PROP_BIN_TESTE, "411111");
		final Map<String, Object> c = new LinkedHashMap<>();
		c.put("bin", bin);
		c.put("end", d.length() >= 4 ? d.substring(d.length() - 4) : "0000");
		c.put("ownerName", StringUtils.left(titular, 150));
		return c;
	}

	protected Integer parcelas(final OrderModel order)
	{
		final Integer n = SocioPayments.integer(modelService, order, SocioPayments.INSTALLMENTS);
		return n == null || n.intValue() < 1 ? Integer.valueOf(1) : n;
	}

	/** Telefone do cadastro (Customer.phone) e, sem ele, o do endereço; tipo 6 = celular, 1 = residencial */
	protected List<Map<String, Object>> telefones(final CustomerModel cliente, final OrderModel order)
	{
		String tel = attr(cliente, "phone");
		if (!Telefone.valido(tel))
		{
			for (final AddressModel a : Arrays.asList(order.getDeliveryAddress(), order.getPaymentAddress()))
			{
				if (a != null && Telefone.valido(a.getPhone1()))
				{
					tel = a.getPhone1();
					break;
				}
			}
		}
		if (!Telefone.valido(tel))
		{
			return Collections.emptyList();
		}
		final Map<String, Object> t = new LinkedHashMap<>();
		t.put("type", Integer.valueOf(Telefone.digitos(tel).length() == 11 ? 6 : 1));
		t.put("ddi", Integer.valueOf(55));
		t.put("ddd", Integer.valueOf(Telefone.ddd(tel)));
		t.put("number", Long.valueOf(Telefone.numero(tel)));
		return Collections.singletonList(t);
	}

	/** Endereço só com os campos obrigatórios presentes (rua, número, bairro, cidade, UF, CEP); senão null */
	protected Map<String, Object> endereco(final AddressModel a)
	{
		if (a == null || StringUtils.isAnyBlank(a.getStreetname(), a.getTown(), a.getPostalcode()))
		{
			return null;
		}
		final String numero = StringUtils.defaultString(a.getStreetnumber()).trim();
		final boolean ehNumero = numero.matches("\\d+[A-Za-z]?");
		final Map<String, Object> e = new LinkedHashMap<>();
		e.put("street", StringUtils.left(a.getStreetname(), 200));
		e.put("number", ehNumero ? numero : "S/N");
		if (!ehNumero && !numero.isEmpty())
		{
			e.put("additionalInformation", StringUtils.left(numero, 250));
		}
		e.put("county", StringUtils.left(StringUtils.defaultIfBlank(a.getDistrict(), "Centro"), 150));
		e.put("city", StringUtils.left(a.getTown(), 150));
		String uf = a.getRegion() == null ? null : StringUtils.defaultIfBlank(a.getRegion().getIsocodeShort(),
				StringUtils.substringAfterLast(a.getRegion().getIsocode(), "-"));
		e.put("state", StringUtils.defaultIfBlank(uf, "RJ"));
		e.put("zipcode", digits(a.getPostalcode()));
		e.put("country", "Brasil");
		return e;
	}

	@Override
	public boolean ativo()
	{
		try
		{
			return ligado(baseStoreService.getCurrentBaseStore());
		}
		catch (final RuntimeException e)
		{
			return false;
		}
	}

	@Override
	public Map<String, Object> configLoja()
	{
		final Map<String, Object> m = new LinkedHashMap<>();
		BaseStoreModel store = null;
		try
		{
			store = baseStoreService.getCurrentBaseStore();
		}
		catch (final RuntimeException e)
		{
			// sem loja na sessão
		}
		final boolean on = ligado(store);
		m.put("ativo", Boolean.valueOf(on));
		m.put("fingerprint", on ? str(store, "clearSaleFingerprint") : null);
		return m;
	}

	protected boolean ligado(final BaseStoreModel store)
	{
		if (store == null || !configurationService.getConfiguration().getBoolean(PROP_ATIVO, true))
		{
			return false;
		}
		final Object on = attrObj(store, "clearSaleAtivo");
		return Boolean.TRUE.equals(on) && StringUtils.isNoneBlank(str(store, "clearSaleUsuario"), str(store, "clearSaleSenha"));
	}

	/** Token do POST /v1/authenticate, reaproveitado até 1 minuto antes de vencer */
	protected String token(final String url, final String usuario, final String senha, final int timeout, final boolean renovar)
			throws IOException
	{
		final String chave = url + "|" + usuario;
		final Object[] guardado = tokens.get(chave);
		if (!renovar && guardado != null && ((Long) guardado[1]).longValue() > System.currentTimeMillis() + 60_000L)
		{
			return (String) guardado[0];
		}
		final Map<String, Object> login = new LinkedHashMap<>();
		login.put("name", usuario);
		login.put("password", senha);
		final ClearSaleClient.Resposta r = clearSaleClient.post(url + "/v1/authenticate", null, login, timeout);
		final Object token = r.corpo.get("Token") != null ? r.corpo.get("Token") : r.corpo.get("token");
		if (!r.ok() || token == null)
		{
			throw new IOException("login no ClearSale recusado (HTTP " + r.status + ")");
		}
		final Object exp = r.corpo.get("ExpirationDate") != null ? r.corpo.get("ExpirationDate") : r.corpo.get("expirationDate");
		tokens.put(chave, new Object[] { token.toString(), Long.valueOf(validade(exp)) });
		return token.toString();
	}

	protected long validade(final Object exp)
	{
		final String s = exp == null ? "" : exp.toString().trim();
		try
		{
			return OffsetDateTime.parse(s).toInstant().toEpochMilli();
		}
		catch (final RuntimeException e)
		{
			// sem fuso: hora de Brasília
		}
		try
		{
			return LocalDateTime.parse(s).atZone(ZoneId.of("America/Sao_Paulo")).toInstant().toEpochMilli();
		}
		catch (final RuntimeException e)
		{
			return Instant.now().plusSeconds(1800).toEpochMilli();
		}
	}

	protected void registrar(final OrderModel order, final String status, final Double score, final String requestId,
			final String mensagem)
	{
		try
		{
			modelService.setAttributeValue(order, "clearSaleStatus", status);
			modelService.setAttributeValue(order, "clearSaleScore", score);
			modelService.setAttributeValue(order, "clearSaleRequestId", requestId);
			modelService.setAttributeValue(order, "clearSaleDate", new Date());
			modelService.setAttributeValue(order, "clearSaleMessage", StringUtils.left(mensagem, 2000));
			modelService.save(order);
		}
		catch (final RuntimeException e)
		{
			LOG.error("Pedido " + order.getCode() + ": resultado do ClearSale não gravado (" + mensagem + ")", e);
		}
	}

	protected static Map<?, ?> primeiro(final Object lista)
	{
		if (lista instanceof List && !((List<?>) lista).isEmpty() && ((List<?>) lista).get(0) instanceof Map)
		{
			return (Map<?, ?>) ((List<?>) lista).get(0);
		}
		return null;
	}

	protected static Double numero(final Object v)
	{
		if (v instanceof Number)
		{
			return Double.valueOf(((Number) v).doubleValue());
		}
		try
		{
			return v == null ? null : Double.valueOf(v.toString().replace(',', '.'));
		}
		catch (final NumberFormatException e)
		{
			return null;
		}
	}

	protected static Set<String> lista(final String csv)
	{
		return Arrays.stream(StringUtils.defaultString(csv).split("[,;\\s]+")).map(String::trim).filter(StringUtils::isNotEmpty)
				.map(String::toLowerCase).collect(Collectors.toSet());
	}

	protected static Double dinheiro(final Double v)
	{
		return Double.valueOf(v == null ? 0d : Math.round(v.doubleValue() * 100d) / 100d);
	}

	protected static String digits(final String s)
	{
		return s == null ? "" : s.replaceAll("\\D", "");
	}

	protected static String data(final Date d)
	{
		final SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
		f.setTimeZone(TimeZone.getTimeZone("America/Sao_Paulo"));
		return f.format(d == null ? new Date() : d);
	}

	protected String attr(final Object item, final String qualifier)
	{
		final Object v = attrObj(item, qualifier);
		return v == null ? null : v.toString();
	}

	protected Object attrObj(final Object item, final String qualifier)
	{
		try
		{
			return item == null ? null : modelService.getAttributeValue(item, qualifier);
		}
		catch (final RuntimeException e)
		{
			return null; // antes do Update o atributo ainda não existe
		}
	}

	protected String str(final Object item, final String qualifier)
	{
		final String v = attr(item, qualifier);
		return StringUtils.isBlank(v) ? null : v.trim();
	}

	public void setClearSaleClient(final ClearSaleClient clearSaleClient)
	{
		this.clearSaleClient = clearSaleClient;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}

	public void setBaseStoreService(final BaseStoreService baseStoreService)
	{
		this.baseStoreService = baseStoreService;
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	public void setEventService(final EventService eventService)
	{
		this.eventService = eventService;
	}

	public void setSeatHoldService(final SeatHoldService seatHoldService)
	{
		this.seatHoldService = seatHoldService;
	}

	public void setMatchdayTicketService(final MatchdayTicketService matchdayTicketService)
	{
		this.matchdayTicketService = matchdayTicketService;
	}

	public void setSocioExperienceService(final SocioExperienceService socioExperienceService)
	{
		this.socioExperienceService = socioExperienceService;
	}

	public void setSocioPlanService(final SocioPlanService socioPlanService)
	{
		this.socioPlanService = socioPlanService;
	}
}
