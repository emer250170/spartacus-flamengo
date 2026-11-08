package br.com.flamengo.matchday.estudio;

import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.user.CustomerModel;

import java.util.List;
import java.util.Map;


/**
 * Mengo Estúdio no pós-venda (08/11, PR 4): a situação própria da parte sob demanda do pedido
 * (Recebido → Em produção → Enviado → Entregue; Cancelado), a linha do tempo, o cancelamento conversado com a
 * estamparia ({@link MengoEstampariaGateway}) e a recusa simulada do pagamento (painel da demo).
 * <p>
 * Mudar a situação grava um evento na linha do tempo e, na produção, o número de série de cada camiseta; no envio, o
 * código de rastreio ({@code MengoPedidoSituacaoInterceptor}). Vale tanto pela loja quanto pelo Backoffice.
 * Nada muda com flamengo.estudio.ativo=false.
 */
public interface MengoEstudioOrderService
{
	String RECEBIDO = "RECEBIDO";
	String EM_PRODUCAO = "EM_PRODUCAO";
	String ENVIADO = "ENVIADO";
	String ENTREGUE = "ENTREGUE";
	String CANCELADO = "CANCELADO";

	/** O pedido (ou carrinho) tem camiseta do Estúdio */
	boolean temEstudio(AbstractOrderModel order);

	/** Só camisetas do Estúdio (nenhum item da loja) */
	boolean soEstudio(AbstractOrderModel order);

	/** Código da situação do Estúdio no pedido (null = pedido sem camiseta do Estúdio ou anterior ao PR 4) */
	String situacao(AbstractOrderModel order);

	/** Pedido novo com camiseta do Estúdio: situação Recebido (gancho do fechamento do pedido) */
	void iniciar(OrderModel order);

	/** Muda a situação com o texto que aparece na linha do tempo e quem fez */
	void mudar(OrderModel order, String situacao, String nota, String autor);

	/** Pedido do torcedor pelo código (null = não existe ou é de outro torcedor) */
	OrderModel pedido(CustomerModel customer, String code);

	/** Pedidos do torcedor, mais novos primeiro: código, data, total, origem (loja, estudio) e situação do Estúdio */
	List<Map<String, Object>> pedidos(CustomerModel customer);

	/** Situação, linha do tempo, rastreio, camisetas com número de série e se ainda dá para cancelar */
	Map<String, Object> detalhe(OrderModel order);

	/**
	 * Cancelamento pedido pelo torcedor (só em Recebido): a estamparia aceita (cancela e registra o estorno simulado),
	 * recusa porque começou a produção, ou a comunicação falha (nada muda). Devolve "resultado" (ACEITO, RECUSADO ou
	 * FALHA) e "mensagem". Situação que não permite cancelar: IllegalArgumentException com o motivo.
	 */
	Map<String, Object> cancelar(OrderModel order, String autor);

	/**
	 * Recusa simulada do próximo pagamento com cartão (Simulações da demo › Próximo pagamento = RECUSAR_UMA_VEZ ou
	 * RECUSAR), só para carrinho com camiseta do Estúdio. Devolve a mensagem da recusa ou null (pode pagar).
	 * RECUSAR_UMA_VEZ volta para APROVAR depois de usada.
	 */
	String recusaPagamento(AbstractOrderModel cart);
}
