package br.com.flamengo.matchday.socio;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;


/**
 * Forma de pagamento do plano (item 7 do plano de evolução): o que o checkout
 * oferece para o carrinho com plano e a situação do pagamento de um pedido.
 * Montado pelo {@link SocioPaymentService}.
 */
public class SocioPayment
{
	/** Chave flamengo.socio.pagamento.ativo */
	public boolean enabled;
	/** Plano no carrinho/pedido (código e nome) */
	public String planCode;
	public String planName;
	/** O plano aceita o ano inteiro à vista no Pix e no boleto */
	public boolean upfrontAllowed;
	/** Mensalidade (plano + convidados) e o ano inteiro (12 mensalidades) */
	public double monthly;
	public int months;
	public double annual;
	/** Formas que o checkout oferece (CARTAO_MENSAL, CARTAO_ANUAL, PIX, BOLETO) */
	public final List<String> methods = new ArrayList<>();
	/** Parcelas no cartão (ano inteiro): até quantas */
	public int maxInstallments;

	/** Forma escolhida (null = cartão, mensalidade, como antes) e as parcelas */
	public String method;
	public Integer installments;
	/** Total do carrinho ou do pedido */
	public double total;

	/** Pedido (pagamentos já feitos) */
	public String orderCode;
	public Date orderDate;
	/** Pix e boleto: vencimento, código fictício e a confirmação */
	public Date due;
	public String code;
	public Date confirmedAt;
	/** Pix ou boleto ainda não confirmado */
	public boolean awaiting;
	/** Uma linha: "Pix de R$ 805,20 (vence 02/11/2026 18:30)" */
	public String summary;
}
