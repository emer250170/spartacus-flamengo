package br.com.flamengo.matchday.payment.adyen;

import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.order.OrderModel;

import java.util.Map;


/**
 * Pagamento pela Adyen (05/11), pela API de Checkout no fluxo Sessions:
 * <ol>
 * <li>a loja pede uma sessão para o carrinho ({@link #createSession});</li>
 * <li>o Drop-in da Adyen cobra o cartão (com 3D Secure) ou mostra o Pix/boleto;</li>
 * <li>a loja fecha o pedido ({@link #placeOrder}): cartão aprovado fica pago; Pix e boleto ficam
 * aguardando pagamento, como no item 7;</li>
 * <li>o webhook da Adyen ({@link #handleNotifications}) confirma o Pix/boleto: a mesma confirmação
 * do gestor (plano, ingressos, processo do pedido).</li>
 * </ol>
 * Chave {@value #ENABLED_PROPERTY} (false = pagamento simulado de hoje). Conta e chaves na loja base.
 */
public interface AdyenPaymentService
{
	String ENABLED_PROPERTY = "flamengo.pagamento.adyen.ativo";

	/** Chave ligada e loja base com conta, chave de API e client key */
	boolean isEnabled();

	/** Configuração pública para a loja: ligado, client key e ambiente */
	Map<String, Object> clientConfig();

	/** Meios de pagamento que a conta oferece para o carrinho (diagnóstico da conexão) */
	Map<String, Object> paymentMethods(CartModel cart);

	/** Cria a sessão de checkout para o carrinho */
	AdyenSession createSession(CartModel cart);

	/**
	 * Confere o carrinho com as mesmas regras do "Finalizar pedido" (poltronas reservadas, Termo do
	 * Nação, convidados, ingressos de quem não é sócio, forma de pagamento do plano) antes de cobrar o
	 * cartão: a cobrança só acontece se o pedido vai poder ser fechado (05/11).
	 */
	void check(CartModel cart);

	/**
	 * Fecha o pedido depois do Drop-in: confere a sessão na Adyen; cartão só com pagamento concluído,
	 * Pix/boleto com a ação (QR Code ou boleto) apresentada ao torcedor.
	 */
	OrderModel placeOrder(CartModel cart, String sessionId, String sessionResult, String resultCode,
			Map<String, Object> action);

	/** Propriedade com os meios que passam pela Adyen (05/11: pix,boletobancario; cartão = scheme) */
	String METHODS_PROPERTY = "flamengo.pagamento.adyen.meios";

	/**
	 * Meios que passam pela Adyen agora (scheme, pix, boletobancario), conforme
	 * {@value #METHODS_PROPERTY}; vazio com a Adyen desligada. O que não está na lista segue o
	 * pagamento simulado de hoje (o cartão, enquanto a Adyen não libera Visa/Mastercard).
	 */
	java.util.List<String> methods();

	/**
	 * Pix ou boleto pela Adyen sem o componente da Adyen (05/11): o servidor chama /payments, grava
	 * no carrinho o código (Pix copia e cola ou linha digitável), o vencimento e a referência, e a
	 * loja fecha o pedido aguardando pagamento como no item 7; o webhook confirma.
	 *
	 * @param pix
	 *           true = Pix, false = boleto
	 * @param cpf
	 *           CPF do pagador (só dígitos; obrigatório no boleto)
	 * @return a ação da Adyen (qrCode ou voucher)
	 */
	Map<String, Object> prepareUpfront(CartModel cart, boolean pix, String cpf);

	/** Provedor das transações de pagamento dos pedidos pagos pela Adyen (comandos do SAP: estorno, cancelamento) */
	String PROVIDER = "Adyen";

	/** Estorno na Adyen do pagamento (POST /payments/{psp}/refunds); devolve a resposta (pspReference do estorno, status) */
	Map<String, Object> refundPayment(String pspReference, long cents, String currency, String reference);

	/** Cancelamento do pagamento na Adyen (POST /payments/{psp}/cancels) */
	Map<String, Object> cancelPayment(String pspReference, String reference);

	/**
	 * Estorna pela Adyen parte do pedido pago por ela (ingresso cancelado, arrependimento do plano):
	 * lança o estorno na transação da Adyen do pedido pelo PaymentService do SAP. Pedido sem
	 * pagamento na Adyen (ou Adyen desligada): nada acontece, como hoje (reembolso só registrado).
	 *
	 * @return "Estorno de R$ X pela Adyen (referência ...)", ou null se o pedido não foi pago pela Adyen
	 */
	String refund(String orderCode, double amount, String reason);

	/** Pagamento do pedido na Adyen para a loja: meio, pspReference, ação (Pix/boleto) e situação */
	Map<String, Object> orderPayment(AbstractOrderModel order);

	/**
	 * Avisos do webhook: confere usuário/senha e HMAC, registra cada aviso e aplica ao pedido.
	 *
	 * @return quantos avisos foram aceitos
	 */
	int handleNotifications(Map<String, Object> body, String authorization);
}
