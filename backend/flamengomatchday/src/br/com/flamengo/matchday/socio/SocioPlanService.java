package br.com.flamengo.matchday.socio;

import br.com.flamengo.matchday.model.SocioPlanModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.product.ProductModel;

import java.util.List;


/**
 * Planos de sócio-torcedor cadastrados no Backoffice (perspectiva "Sócio-Torcedor").
 */
public interface SocioPlanService
{
	/** Prefixo do marcador com as regras do plano no fim da descrição do produto (lido pela loja) */
	String MARKER = "socio-plan";

	/** Todos os planos, na ordem dos cards */
	List<SocioPlanModel> getPlans();

	SocioPlanModel findByCode(String code);

	/** Plano vendido por este produto (convidados e outros produtos: null) */
	SocioPlanModel findForProduct(ProductModel product);

	/** Código do produto a partir do nome ("Nação Sem Fronteiras Anual" -> NACAO-SEM-FRONTEIRAS-ANUAL) */
	String generateCode(SocioPlanModel plan);

	/**
	 * Publica o plano na loja conforme a situação: produto de assinatura, preço
	 * mensal, vagas, convidado, grupos de clientes, benefícios e promoção de
	 * desconto no ingresso. Fora da loja / rascunho: tira o produto da loja.
	 *
	 * @return mensagem para o usuário
	 */
	String publish(SocioPlanModel plan);

	/** Pedido fechado com plano: o cliente fica só no grupo do melhor plano comprado */
	void applyOrder(AbstractOrderModel order);

	/** Titulares ativos (no grupo do plano e sem o marcador de carrinho) */
	int countMembers(SocioPlanModel plan);

	/**
	 * Cancelamento confirmado: tira o torcedor dos grupos de todos os planos (e
	 * dos grupos de plano no carrinho). Não salva o cliente.
	 *
	 * @return códigos dos planos de que ele era sócio (vazio se não era)
	 */
	List<String> removeMembership(de.hybris.platform.core.model.user.CustomerModel customer);

	// ------------------------------------------------------------ situação do sócio

	/** Grupo de quem está com os benefícios suspensos por inadimplência (continua sócio) */
	String SUSPENDED_GROUP = "socio-suspenso";

	/** Liga e desliga a suspensão por inadimplência (padrão: ligada) */
	String SUSPENSION_PROPERTY = "flamengo.socio.suspensao.ativa";

	/** A suspensão por inadimplência está ligada? */
	boolean isSuspensionEnabled();

	/** Plano comprado pelo torcedor (melhor onda), sem contar plano só no carrinho; null se não é sócio */
	SocioPlanModel memberPlan(de.hybris.platform.core.model.user.CustomerModel customer);

	/** O torcedor está com os benefícios suspensos? */
	boolean isSuspended(de.hybris.platform.core.model.user.CustomerModel customer);

	/**
	 * Põe ou tira o torcedor do grupo de suspensos. Não salva o cliente.
	 *
	 * @return true se mudou alguma coisa
	 */
	boolean setSuspended(de.hybris.platform.core.model.user.CustomerModel customer, boolean suspended);

	/** Titulares do plano que estão com os benefícios suspensos */
	int countSuspended(SocioPlanModel plan);

	/**
	 * Evento novo (não salvo) na linha do tempo do torcedor, com o plano atual, a
	 * data de agora e o usuário da sessão como autor.
	 */
	br.com.flamengo.matchday.model.SocioEventModel newEvent(de.hybris.platform.core.model.user.CustomerModel customer,
			br.com.flamengo.matchday.enums.SocioEventType type, String origin, String note);
}
