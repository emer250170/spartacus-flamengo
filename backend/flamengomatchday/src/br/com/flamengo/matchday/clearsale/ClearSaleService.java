package br.com.flamengo.matchday.clearsale;

import de.hybris.platform.orderprocessing.model.OrderProcessModel;

import java.util.Map;


/**
 * Análise de fraude do ClearSale, Realtime Decision (07/11).
 * <p>
 * O passo {@code clearSaleCheck} do order-process (versão dinâmica 9) chama {@link #analisar}. O pedido vai
 * para {@code POST /v1/orders} e a decisão volta na hora:
 * <ul>
 * <li>aprovado (APA, APP...) ou sem resposta: o pedido segue (passo fraudCheck de sempre);</li>
 * <li>recusado ({@code flamengo.clearsale.recusa}, padrão RPA,RPP,RPM,SUS,FRD): o pedido é cancelado sem
 * cobrança, o estoque e a poltrona voltam, os ingressos e vouchers são cancelados e sai o e-mail
 * "Pedido cancelado".</li>
 * </ul>
 * Ligado quando a propriedade {@code flamengo.clearsale.ativo} (padrão true) e o campo "Análise de fraude
 * ClearSale" da loja base (Backoffice, aba ClearSale) estão ligados e há usuário e senha.
 */
public interface ClearSaleService
{
	String OK = "OK";
	String RECUSADO = "RECUSADO";

	/** Analisa o pedido do processo e devolve {@link #OK} ou {@link #RECUSADO} (pedido já cancelado) */
	String analisar(OrderProcessModel process);

	/** A análise está ligada para a loja atual? */
	boolean ativo();

	/** Para a loja: {"ativo": true/false, "fingerprint": "chave pública"} */
	Map<String, Object> configLoja();
}
