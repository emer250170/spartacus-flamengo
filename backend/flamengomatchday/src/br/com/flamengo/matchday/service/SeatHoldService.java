package br.com.flamengo.matchday.service;

import br.com.flamengo.matchday.model.SeatHoldModel;
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

	/**
	 * Desfaz o {@link #commitSale}: pedido recusado antes do pagamento (análise de fraude, 07/11). Devolve ao
	 * estoque as mesmas linhas que a venda baixou (poltrona volta a ficar livre) e troca a marca do pedido
	 * para {@code matchday:estoque-devolvido}. Pedido sem a marca de venda: não faz nada.
	 */
	void revertSale(AbstractOrderModel order);

	/** Solta as reservas vencidas; devolve quantas. */
	int releaseExpired();

	/**
	 * A reserva foi apagada fora deste servico (Backoffice): em segundo plano,
	 * devolve a poltrona a venda e a tira do carrinho que a segurava.
	 */
	void handleExternalRemoval(SeatHoldModel hold);
}
