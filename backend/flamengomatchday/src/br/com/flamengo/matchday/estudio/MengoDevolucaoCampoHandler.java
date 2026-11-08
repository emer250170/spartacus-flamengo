package br.com.flamengo.matchday.estudio;

import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.servicelayer.model.attribute.DynamicAttributeHandler;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;


/**
 * Campos legíveis da devolução do Mengo Estúdio no Backoffice (08/11, depois do PR 5): cada atributo dinâmico
 * (mengoDevMotivo, mengoDevSolucao, mengoDevEtapa...) lê um campo do JSON gravado em AbstractOrder.mengoDevolucao
 * e o devolve em texto, em português. Só leitura; o JSON continua sendo a fonte. Pedido sem devolução: vazio.
 */
public class MengoDevolucaoCampoHandler implements DynamicAttributeHandler<String, AbstractOrderModel>
{
	private MengoEstudioOrderService mengoEstudioOrderService;
	/** motivo, solucao, etapa, codigoPostagem, comentario, solicitadaEm, enviadaEm, recebidaEm, valor, reimpressao */
	private String campo;

	@Override
	public String get(final AbstractOrderModel order)
	{
		if (order == null || order.getPk() == null)
		{
			return null;
		}
		final Map<String, Object> d = mengoEstudioOrderService.devolucao(order);
		if (d == null)
		{
			return null;
		}
		final Object v;
		switch (campo)
		{
			case "motivo":
				v = d.get("motivo");
				return "DEFEITO".equals(v) ? "Defeito na peça"
						: "ERRO".equals(v) ? "Erro de produção" : "ARREPENDIMENTO".equals(v) ? "Arrependimento" : texto(v);
			case "solucao":
				v = d.get("solucao");
				return "REIMPRESSAO".equals(v) ? "Reimpressão sem custo" : "REEMBOLSO".equals(v) ? "Reembolso" : texto(v);
			case "etapa":
				v = d.get("etapa");
				return "SOLICITADA".equals(v) ? "Solicitada (aguardando o envio da peça)"
						: "ENVIADA".equals(v) ? "Peça enviada (aguardando a estamparia)"
								: "RESOLVIDA".equals(v) ? "Concluída" : texto(v);
			case "valor":
				return texto(d.get("valorTexto"));
			case "reimpressao":
				return texto(d.get("pedidoReimpressao"));
			case "solicitadaEm":
			case "enviadaEm":
			case "recebidaEm":
				return data(d.get(campo));
			default:
				return texto(d.get(campo));
		}
	}

	@Override
	public void set(final AbstractOrderModel order, final String value)
	{
		throw new UnsupportedOperationException("campo da devolução é só leitura");
	}

	private static String texto(final Object v)
	{
		return v == null ? null : String.valueOf(v);
	}

	/** "08/11/2026 10:52" (horário de Brasília) */
	private static String data(final Object v)
	{
		if (!(v instanceof Number))
		{
			return null;
		}
		final SimpleDateFormat f = new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("pt", "BR"));
		f.setTimeZone(TimeZone.getTimeZone("America/Sao_Paulo"));
		return f.format(new Date(((Number) v).longValue()));
	}

	public void setMengoEstudioOrderService(final MengoEstudioOrderService mengoEstudioOrderService)
	{
		this.mengoEstudioOrderService = mengoEstudioOrderService;
	}

	public void setCampo(final String campo)
	{
		this.campo = campo;
	}
}
