package br.com.flamengo.matchday.estudio;

import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.CustomerModel;

import java.util.List;
import java.util.Map;


/**
 * Mengo Estúdio no carrinho (08/11, PR 3): a camiseta e a estampa formam um item só. Cada combinação de estampa,
 * camiseta (modelo e tecido), cor e tamanho é uma linha do carrinho; repetir a mesma combinação soma a quantidade.
 * Estampa que sai de linha trava o fechamento do pedido. Recusas: {@link IllegalArgumentException} com a mensagem
 * para a loja. Chave flamengo.estudio.ativo.
 */
public interface MengoEstudioCartService
{
	/** Produto é uma camiseta do Mengo Estúdio (tem mengoModelo) */
	boolean isCamiseta(ProductModel product);

	/** Linha do carrinho/pedido com camiseta do Estúdio (tem estampa) */
	boolean isEstudioEntry(AbstractOrderEntryModel entry);

	/**
	 * Carrinho do torcedor logado: pelo código; "current" = o mais recente da loja ou um novo.
	 * Recusa com IllegalArgumentException se o carrinho não é dele.
	 */
	CartModel carrinho(CustomerModel owner, String cartId);

	/**
	 * Inclui (ou soma) a camiseta com a estampa. substituir = número da linha que está sendo editada (sai do carrinho
	 * depois que a nova configuração é aceita). Devolve o número da linha e a quantidade nela.
	 */
	Map<String, Object> adicionar(CartModel cart, String estampa, String camiseta, String cor, String tamanho,
			int quantidade, Integer substituir);

	/** Linhas do Estúdio do carrinho ou do pedido: estampa, camiseta, cor, tamanho, situação da estampa */
	List<Map<String, Object>> itens(AbstractOrderModel order);

	/** Mensagem quando o carrinho tem estampa que saiu de linha (não pode fechar); null quando pode */
	String bloqueio(AbstractOrderModel cart);

	/** Quantidade máxima por linha (flamengo.estudio.quantidade.max) */
	int quantidadeMax();
}
