package br.com.flamengo.matchday.service;

import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.product.ProductModel;


/**
 * Reserva temporaria de poltrona.
 * <p>
 * A reserva usa o campo reserved do StockLevel: o banco so aceita reservar se
 * ainda houver unidade livre, entao duas pessoas nunca seguram a mesma poltrona.
 */
public interface SeatHoldService
{
	/** A variante e uma poltrona de jogo? */
	boolean isSeat(ProductModel product);

	/**
	 * Garante a poltrona para o carrinho.
	 *
	 * @param renew
	 *           se a reserva ja e deste carrinho, renova o prazo
	 * @return false quando outra pessoa esta com a poltrona
	 */
	boolean hold(ProductModel seat, AbstractOrderModel cart, boolean renew);

	/** Se o mesmo torcedor segura a poltrona em outro carrinho, solta para poder traze-la a este. */
	void releaseIfHeldElsewhereBySameUser(ProductModel seat, AbstractOrderModel cart);

	/** Solta a poltrona se a reserva for deste carrinho. */
	void release(ProductModel seat, AbstractOrderModel cart);

	/** Converte as reservas do pedido em venda e baixa o estoque de todos os itens. */
	void commitSale(AbstractOrderModel order);

	/** Solta as reservas vencidas; devolve quantas. */
	int releaseExpired();
}
