package br.com.flamengo.matchday.socio;

import java.util.Date;


/**
 * Simulação da saída do sócio (item 5 do plano de evolução): reembolso dentro do
 * prazo de arrependimento ou multa sobre as mensalidades restantes do contrato.
 * <p>
 * Também usada para o valor final de um cancelamento já confirmado (lido do
 * evento CANCELAMENTO): {@link #confirmed} = true.
 */
public class SocioExitQuote
{
	/** Código e nome do plano */
	public String planCode;
	public String planName;
	/** Pedido da adesão ao plano atual (início do contrato) */
	public String orderCode;
	/** Início do contrato (data do pedido do plano atual) */
	public Date start;
	/** Data que vale para o cálculo: o pedido de cancelamento (ou agora, numa simulação) */
	public Date basis;

	/** Prazo de arrependimento em dias corridos e o último dia dele */
	public int withdrawalDays;
	public Date withdrawalUntil;
	/** O pedido está dentro do prazo de arrependimento */
	public boolean withinWithdrawal;

	/** Mensalidades do contrato (12), pagas até a data-base e restantes */
	public int installments;
	public int paidInstallments;
	public int remainingInstallments;
	/** Mensalidade com convidados (R$) e convidados contratados */
	public double monthly;
	public int guests;
	/** Pago na adesão: plano + convidados (R$) */
	public double paidAtJoin;

	/** Percentual da multa e os valores (R$) */
	public double finePercent;
	public double refund;
	public double fine;

	/** Valor de um cancelamento já confirmado (evento CANCELAMENTO), não uma simulação */
	public boolean confirmed;
	/** A saída foi o fim da vigência sem renovação (evento FIM_VIGENCIA): sem multa */
	public boolean endOfTerm;
	/** Chamado de cancelamento em aberto (a data-base é a abertura dele) */
	public String openTicket;
}
