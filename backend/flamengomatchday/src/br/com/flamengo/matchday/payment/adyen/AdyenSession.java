package br.com.flamengo.matchday.payment.adyen;

import java.util.List;
import java.util.Map;


/** Sessão de checkout criada na Adyen para o carrinho (o Drop-in da loja usa id e sessionData) */
public class AdyenSession
{
	public String id;
	public String sessionData;
	public String reference;
	/** Em centavos */
	public long amount;
	public String currency;
	/** scheme, pix, boletobancario */
	public List<String> allowedPaymentMethods;
	/** Parcelas do cartão oferecidas (plano no ano inteiro); vazio = à vista */
	public List<Integer> installments;
	public Integer preselectedInstallments;
	public String clientKey;
	public String environment;
	/** Dados do comprador já conhecidos (o boleto pede nome, CPF e endereço) */
	public Map<String, Object> shopper;
}
