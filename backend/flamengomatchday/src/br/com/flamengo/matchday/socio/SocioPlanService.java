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
}
