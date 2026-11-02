package br.com.flamengo.matchday.socio;

import br.com.flamengo.matchday.model.MatchdayGameModel;
import de.hybris.platform.commerceservices.order.CommerceCartModificationException;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.CustomerModel;

import java.util.Date;


/**
 * Elegibilidade para comprar ingresso: confere plano, situação (suspenso compra
 * na venda geral), janela da onda, biometria e limite de ingressos por jogo, e
 * grava cada tentativa (SocioEligibilityDecision), aprovada ou negada.
 * <p>
 * Chaves (project.properties / local.properties):
 * <ul>
 * <li>{@value #RECORD_PROPERTY} (padrão true): grava as decisões;</li>
 * <li>{@value #BLOCK_PROPERTY} (padrão false): recusa a poltrona quando negado; desligado = só observa;</li>
 * <li>{@value #BIOMETRY_REQUIRED_PROPERTY} (padrão false): biometria pendente nega a compra;</li>
 * <li>{@value #GENERAL_LIMIT_PROPERTY} (padrão 4): ingressos por jogo para quem não é sócio.</li>
 * </ul>
 * O limite do sócio é o titular mais os convidados do plano (1 + maxGuests).
 */
public interface SocioEligibilityService
{
	String RECORD_PROPERTY = "flamengo.socio.elegibilidade.registro";
	String BLOCK_PROPERTY = "flamengo.socio.elegibilidade.bloqueio";
	String BIOMETRY_REQUIRED_PROPERTY = "flamengo.socio.biometria.exigida";
	String GENERAL_LIMIT_PROPERTY = "flamengo.ingressos.limite.geral";

	/** Grupo de quem já validou a biometria facial (dentro de customergroup) */
	String BIOMETRY_GROUP = "biometria-validada";

	/** Onda 4 = venda geral */
	int GENERAL_SALE = 4;

	/**
	 * Confere se o torcedor pode pôr mais {@code quantity} ingressos do jogo no
	 * carrinho agora. Não grava nada.
	 *
	 * @param cart
	 *           carrinho atual (pode ser null)
	 * @param seat
	 *           poltrona que está entrando (não conta como ingresso do carrinho; pode ser null)
	 */
	SocioEligibility evaluate(CustomerModel customer, MatchdayGameModel game, CartModel cart, ProductModel seat, long quantity,
			Date now);

	/**
	 * Chamado ao pôr uma poltrona no carrinho: confere, grava a decisão e, se negado
	 * com o bloqueio ligado, recusa com o motivo. Falha na conferência nunca impede
	 * a compra.
	 */
	void checkAddToCart(CartModel cart, ProductModel seat, long quantity) throws CommerceCartModificationException;

	/** Jogo da poltrona (null se o produto não é poltrona de jogo cadastrado) */
	MatchdayGameModel gameOf(ProductModel seat);

	/** O torcedor validou a biometria? */
	boolean hasBiometry(CustomerModel customer);

	/**
	 * Põe ou tira o torcedor do grupo de biometria validada (cria o grupo se faltar).
	 * Não salva o cliente.
	 *
	 * @return true se mudou alguma coisa
	 */
	boolean setBiometry(CustomerModel customer, boolean validated);

	/** O bloqueio está ligado? */
	boolean isBlocking();
}
