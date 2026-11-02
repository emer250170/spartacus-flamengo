package br.com.flamengo.matchday.socio;

import br.com.flamengo.matchday.model.SocioTermAcceptanceModel;
import br.com.flamengo.matchday.model.SocioTermVersionModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.user.CustomerModel;

import java.util.List;


/**
 * Termo de adesão do programa Nação, versionado (item 4 do plano de evolução).
 * <ul>
 * <li>A versão atual é a última publicada ({@code publishedAt} preenchido e não futuro).</li>
 * <li>Pedido com plano só fecha com o aceite da versão atual para aquele carrinho
 * ({@link #checkCart}); ao fechar, o aceite ganha o número do pedido e vira evento
 * na linha do tempo ({@link #linkOrder}).</li>
 * <li>Versão nova: o sócio aceita na área do sócio ({@link #accept} sem carrinho). Não
 * aceitar não bloqueia nada, só aparece como pendente.</li>
 * </ul>
 * Chave {@value #ENABLED_PROPERTY} (padrão true). Desligada, ou sem versão publicada,
 * o checkout e a área do sócio ficam como antes.
 */
public interface SocioTermService
{
	String ENABLED_PROPERTY = "flamengo.socio.termo.ativo";

	String ORIGIN_CHECKOUT = "Checkout";
	String ORIGIN_MEMBER_AREA = "Área do sócio";
	String ORIGIN_MIGRATION = "Migração";

	boolean isEnabled();

	/** Versão atual (última publicada); null se nenhuma */
	SocioTermVersionModel current();

	/** Aceite mais recente do torcedor (qualquer versão); null se nunca aceitou */
	SocioTermAcceptanceModel lastAcceptance(CustomerModel customer);

	/** O torcedor já aceitou esta versão (em qualquer lugar)? */
	boolean hasAccepted(CustomerModel customer, SocioTermVersionModel version);

	/** O carrinho tem plano de sócio? */
	boolean hasPlan(AbstractOrderModel cart);

	/**
	 * Aceita a versão atual. Com {@code cartCode} (checkout): o carrinho tem de ser do
	 * torcedor; o aceite fica ligado ao carrinho. Sem carrinho (área do sócio): grava
	 * o aceite e o evento na hora. Repetir o aceite não cria outro registro.
	 */
	SocioTermAcceptanceModel accept(CustomerModel customer, String cartCode) throws SocioTermException;

	/** Fechamento do pedido: carrinho com plano precisa do aceite da versão atual para ele */
	void checkCart(CartModel cart) throws SocioTermException;

	/** Pedido fechado: o aceite do carrinho ganha o número do pedido e vira evento */
	void linkOrder(AbstractOrderModel order, String cartCode);

	/** Situação do torcedor (área do sócio, checkout e ficha do Backoffice) */
	SocioTermStatus status(CustomerModel customer);

	/**
	 * Sócios ativos (com plano comprado) que ainda não aceitaram a versão atual. Vazio
	 * se não há versão publicada.
	 */
	List<CustomerModel> pending();
}
