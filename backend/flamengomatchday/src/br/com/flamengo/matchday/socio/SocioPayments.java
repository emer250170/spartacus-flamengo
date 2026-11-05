package br.com.flamengo.matchday.socio;

import br.com.flamengo.matchday.enums.SocioPaymentMethod;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.servicelayer.model.ModelService;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;


/**
 * Leitura da forma de pagamento gravada no carrinho/pedido (atributos novos do
 * AbstractOrder, item 7). Sem dependência de serviços: usada pelo order-process
 * ({@link SocioPlanService#applyOrder}), pelas consultas de pedidos do sócio e pelo
 * {@link SocioPaymentService}. Antes do Update do sistema os atributos não existem
 * e tudo se comporta como antes (cartão, mensalidade).
 */
public final class SocioPayments
{
	public static final String METHOD = "socioPaymentMethod";
	public static final String INSTALLMENTS = "socioInstallments";
	public static final String DUE = "socioPaymentDue";
	public static final String CODE = "socioPaymentCode";
	public static final String CONFIRMED = "socioPaymentConfirmedAt";

	/** Mensalidades cobradas no ano inteiro */
	public static final int MONTHS = 12;

	private static final TimeZone SAO_PAULO = TimeZone.getTimeZone("America/Sao_Paulo");

	private SocioPayments()
	{
	}

	/** Forma gravada (null = cartão, mensalidade, como antes) */
	public static SocioPaymentMethod method(final ModelService modelService, final AbstractOrderModel order)
	{
		final Object value = value(modelService, order, METHOD);
		return value instanceof SocioPaymentMethod ? (SocioPaymentMethod) value : null;
	}

	/** A forma cobra o ano inteiro (12 mensalidades do plano e dos convidados) */
	public static boolean annual(final SocioPaymentMethod method)
	{
		return method == SocioPaymentMethod.CARTAO_ANUAL || method == SocioPaymentMethod.PIX || method == SocioPaymentMethod.BOLETO;
	}

	/** Pix, boleto ou Pix Automático: o pedido espera a confirmação do pagamento */
	public static boolean upfront(final SocioPaymentMethod method)
	{
		return method == SocioPaymentMethod.PIX || method == SocioPaymentMethod.BOLETO || method == SocioPaymentMethod.PIX_AUTOMATICO;
	}

	/** Pix à vista ou Pix Automático (QR Code e copia e cola) */
	public static boolean pix(final SocioPaymentMethod method)
	{
		return method == SocioPaymentMethod.PIX || method == SocioPaymentMethod.PIX_AUTOMATICO;
	}

	/** Pedido com Pix ou boleto ainda não pago (e não cancelado) */
	public static boolean isAwaiting(final ModelService modelService, final AbstractOrderModel order)
	{
		return order != null && upfront(method(modelService, order)) && date(modelService, order, CONFIRMED) == null
				&& order.getStatus() != OrderStatus.CANCELLED;
	}

	public static Date date(final ModelService modelService, final AbstractOrderModel order, final String attribute)
	{
		final Object value = value(modelService, order, attribute);
		return value instanceof Date ? (Date) value : null;
	}

	public static Integer integer(final ModelService modelService, final AbstractOrderModel order, final String attribute)
	{
		final Object value = value(modelService, order, attribute);
		return value instanceof Integer ? (Integer) value : null;
	}

	public static String string(final ModelService modelService, final AbstractOrderModel order, final String attribute)
	{
		final Object value = value(modelService, order, attribute);
		return value instanceof String ? (String) value : null;
	}

	/** "Pix", "Boleto", "Cartão (ano inteiro)", "Cartão (mensalidade)" */
	public static String name(final SocioPaymentMethod method)
	{
		if (method == null || method == SocioPaymentMethod.CARTAO_MENSAL)
		{
			return "Cartão (mensalidade)";
		}
		switch (method)
		{
			case PIX:
				return "Pix";
			case PIX_AUTOMATICO:
				return "Pix Autom\u00e1tico (mensalidade)";
			case BOLETO:
				return "Boleto";
			default:
				return "Cartão (ano inteiro)";
		}
	}

	/**
	 * Uma linha sobre o pagamento do pedido: "Pix de R$ 805,20 (vence 02/11/2026 18:30)",
	 * "Boleto de R$ 805,20 pago em 03/11/2026", "Cartão (ano inteiro) em 12x de R$ 67,10".
	 * Null para pedido sem forma gravada.
	 */
	public static String describe(final ModelService modelService, final AbstractOrderModel order)
	{
		final SocioPaymentMethod method = method(modelService, order);
		if (method == null || order == null)
		{
			return null;
		}
		final double total = order.getTotalPrice() == null ? 0 : order.getTotalPrice().doubleValue();
		if (method == SocioPaymentMethod.CARTAO_MENSAL)
		{
			return name(method) + ", " + money(total);
		}
		if (method == SocioPaymentMethod.CARTAO_ANUAL)
		{
			final Integer n = integer(modelService, order, INSTALLMENTS);
			final int parcels = n == null || n.intValue() < 1 ? 1 : n.intValue();
			return name(method) + (parcels == 1 ? " à vista, " + money(total)
					: " em " + parcels + "x de " + money(Math.round(total / parcels * 100) / 100.0));
		}
		final Date confirmed = date(modelService, order, CONFIRMED);
		if (confirmed != null)
		{
			return name(method) + " de " + money(total) + " pago em " + dateTime(confirmed);
		}
		if (order.getStatus() == OrderStatus.CANCELLED)
		{
			return name(method) + " de " + money(total) + " não pago (pedido cancelado)";
		}
		final Date due = date(modelService, order, DUE);
		return name(method) + " de " + money(total) + (due == null ? "" : " (vence " + dateTime(due) + ")");
	}

	public static String money(final double value)
	{
		return NumberFormat.getCurrencyInstance(new Locale("pt", "BR")).format(value).replace(' ', ' ');
	}

	public static String dateTime(final Date date)
	{
		final SimpleDateFormat f = new SimpleDateFormat("dd/MM/yyyy HH:mm");
		f.setTimeZone(SAO_PAULO);
		return f.format(date);
	}

	protected static Object value(final ModelService modelService, final AbstractOrderModel order, final String attribute)
	{
		if (modelService == null || order == null)
		{
			return null;
		}
		try
		{
			return modelService.getAttributeValue(order, attribute);
		}
		catch (final RuntimeException e)
		{
			return null; // atributo ainda não existe (antes do Update do sistema)
		}
	}
}
