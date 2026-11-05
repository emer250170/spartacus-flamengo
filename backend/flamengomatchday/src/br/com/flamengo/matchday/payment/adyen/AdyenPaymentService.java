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
	 * Fecha o pedido depois do Drop-in: confere a sessão na Adyen; cartão só com pagamento concluído,
	 * Pix/boleto com a ação (QR Code ou boleto) apresentada ao torcedor.
	 */
	OrderModel placeOrder(CartModel cart, String sessionId, String sessionResult, String resultCode,
			Map<String, Object> action);

	/** Pagamento do pedido na Adyen para a loja: meio, pspReference, ação (Pix/boleto) e situação */
	Map<String, Object> orderPayment(AbstractOrderModel order);

	/**
	 * Avisos do webhook: confere usuário/senha e HMAC, registra cada aviso e aplica ao pedido.
	 *
	 * @return quantos avisos foram aceitos
	 */
	int handleNotifications(Map<String, Object> body, String authorization);
}
