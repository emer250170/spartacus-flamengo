package br.com.flamengo.matchday.estudio;

import de.hybris.platform.core.model.order.OrderModel;


/**
 * Conversa com a estamparia parceira que produz as camisetas do Mengo Estúdio (08/11, PR 4). Hoje é simulada
 * ({@code SimulatedMengoEstampariaGateway}, comandada pelas Simulações da demo); a integração real troca só o bean.
 */
public interface MengoEstampariaGateway
{
	/** A estamparia aceitou o cancelamento */
	String ACEITO = "ACEITO";
	/** A estamparia recusou: a produção já começou */
	String RECUSADO = "RECUSADO";
	/** Não deu para falar com a estamparia (nada foi cancelado) */
	String FALHA = "FALHA";

	/** Pede o cancelamento da produção do pedido: ACEITO, RECUSADO ou FALHA */
	String cancelar(OrderModel order);
}
