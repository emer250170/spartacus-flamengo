package br.com.flamengo.matchday.socio;

import br.com.flamengo.matchday.model.SocioEventModel;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.user.CustomerModel;


/**
 * Pix e boleto à vista e plano aguardando pagamento (item 7 do plano de evolução).
 * <p>
 * Com plano no carrinho, o checkout oferece: cartão com a mensalidade (como antes),
 * cartão com o ano inteiro em até 12x, e Pix ou boleto com o ano inteiro à vista
 * (12 mensalidades do plano e dos convidados, sem desconto) nos planos que aceitam.
 * Pix e boleto fecham o pedido sem cartão; o pedido fica aguardando pagamento, o
 * torcedor não vira sócio e os ingressos do mesmo pedido ficam reservados. O gestor
 * confirma o pagamento na ficha do Backoffice ou pelo Claude (evento
 * PAGAMENTO_CONFIRMADO) e só então o plano ativa.
 * <p>
 * Chave: flamengo.socio.pagamento.ativo (false = só cartão com a mensalidade, como antes).
 */
public interface SocioPaymentService
{
	String ENABLED_PROPERTY = "flamengo.socio.pagamento.ativo";
	/** Parcelas no cartão (ano inteiro) */
	String INSTALLMENTS_PROPERTY = "flamengo.socio.pagamento.parcelas";
	/** Validade do Pix em minutos e do boleto em dias */
	String PIX_MINUTES_PROPERTY = "flamengo.socio.pagamento.pix.minutos";
	String BOLETO_DAYS_PROPERTY = "flamengo.socio.pagamento.boleto.dias";
	/**
	 * Operação do pedido da loja (item 14): cartão, Pix ou boleto também no carrinho sem
	 * plano; qualquer pedido com Pix ou boleto fica "Aguardando pagamento" e a confirmação é a
	 * mudança do status do pedido para "Pagamento confirmado". false = como antes.
	 */
	String STORE_PROPERTY = "flamengo.loja.operacao.ativo";

	boolean isEnabled();

	/** Chave flamengo.loja.operacao.ativo (e a do pagamento do plano ligada) */
	boolean isStoreEnabled();

	/**
	 * Confirma o Pix ou boleto do pedido (caminho único do item 14): grava a data da
	 * confirmação, passa o status "Aguardando pagamento" para "Pagamento confirmado" e, se o
	 * pedido tem plano, torna o torcedor sócio. Pedido que não está aguardando: não faz nada.
	 *
	 * @return true se confirmou agora
	 */
	boolean confirmOrder(OrderModel order);

	/** O que o checkout oferece para o carrinho e a forma já escolhida */
	SocioPayment options(CartModel cart);

	/**
	 * Escolhe a forma de pagamento do carrinho e recalcula (CARTAO_ANUAL, PIX e BOLETO
	 * cobram o ano inteiro do plano e dos convidados).
	 *
	 * @param method
	 *           CARTAO_MENSAL, CARTAO_ANUAL, PIX ou BOLETO
	 * @param installments
	 *           parcelas no cartão (só CARTAO_ANUAL)
	 */
	SocioPayment choose(CartModel cart, String method, Integer installments) throws SocioPaymentException;

	/** Fecha o pedido do carrinho com Pix ou boleto (sem cartão): o pedido fica aguardando pagamento */
	OrderModel placeOrder(CartModel cart) throws SocioPaymentException;

	/**
	 * Antes de fechar qualquer pedido: Pix ou boleto escolhido não fecha com cartão
	 * (recusa), e a forma some do carrinho que não tem mais plano.
	 */
	void checkCart(CartModel cart) throws SocioPaymentException;

	/** Situação do pagamento de um pedido (null se o pedido não tem forma gravada) */
	SocioPayment status(AbstractOrderModel order);

	/** Pix ou boleto mais novo de um pedido com plano do torcedor ainda não pago (null se não há) */
	OrderModel awaitingOrder(CustomerModel customer);

	/**
	 * Confirma o pagamento do Pix ou boleto do evento PAGAMENTO_CONFIRMADO (o pedido citado
	 * na observação, ou o mais novo aguardando): grava a confirmação e torna o torcedor
	 * sócio do plano do pedido. Preenche o plano e o resultado do evento.
	 */
	void confirm(CustomerModel customer, SocioEventModel event) throws SocioPaymentException;

	/** Multiplicador do preço da linha: 12 para plano e convidados com o ano inteiro, 1 nas demais */
	int priceMultiplier(AbstractOrderEntryModel entry);
}
