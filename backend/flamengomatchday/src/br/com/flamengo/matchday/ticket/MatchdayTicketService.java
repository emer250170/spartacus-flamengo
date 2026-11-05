package br.com.flamengo.matchday.ticket;

import br.com.flamengo.matchday.model.MatchdayTicketEventModel;
import br.com.flamengo.matchday.model.MatchdayTicketModel;
import br.com.flamengo.matchday.model.SocioGuestModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.servicelayer.interceptor.InterceptorContext;

import java.util.Date;
import java.util.List;


/**
 * Ingressos vendidos (item 10 do plano de evolução): um registro por poltrona vendida, criado
 * ao fechar o pedido (e por {@link #backfill()} para os pedidos antigos), com o histórico de
 * cada ação do gestor de jogos. O pedido em si não muda: as ações mexem no ingresso e no
 * estoque das poltronas.
 * <p>
 * Regras: troca de poltrona só no mesmo jogo e setor; reembolso integral até
 * {@code flamengo.matchday.ingressos.reembolso.horasIntegral} horas antes do jogo (48), depois
 * {@code flamengo.matchday.ingressos.reembolso.percentualDepois}% (80) até o início; depois do
 * início não cancela; ingresso utilizado não troca nem cancela. Ingresso cancelado deixa de dar
 * pontos ao sócio.
 * <p>
 * Chave: flamengo.matchday.ingressos.ativo (false = os ingressos continuam registrados, mas as
 * ações ficam recusadas).
 */
public interface MatchdayTicketService
{
	String ENABLED_PROPERTY = "flamengo.matchday.ingressos.ativo";
	/** Pedido com plano em Pix/boleto: ingressos ficam "aguardando pagamento" até a confirmação (04/11) */
	String AWAIT_PAYMENT_PROPERTY = "flamengo.matchday.ingressos.aguardarPagamento";
	String FULL_REFUND_HOURS_PROPERTY = "flamengo.matchday.ingressos.reembolso.horasIntegral";
	String LATE_REFUND_PERCENT_PROPERTY = "flamengo.matchday.ingressos.reembolso.percentualDepois";

	boolean isEnabled();

	/** Cria os ingressos das poltronas do pedido (uma vez por pedido) */
	List<MatchdayTicketModel> createForOrder(AbstractOrderModel order);

	/**
	 * Pagamento do pedido confirmado (Pix/boleto): os ingressos "aguardando pagamento" do pedido
	 * passam a valer (04/11).
	 *
	 * @return quantos ingressos foram liberados
	 */
	int releaseForOrder(AbstractOrderModel order, String author);

	/** Cria os ingressos dos pedidos antigos que ainda não têm; devolve quantos criou */
	int backfill();

	/**
	 * Ingressos para a lista do cockpit, mais novos primeiro.
	 *
	 * @param gameCode vazio = todos os jogos
	 * @param sector   NORTE, SUL, LESTE, OESTE ou vazio
	 * @param status   VALIDO, CANCELADO, UTILIZADO ou vazio
	 * @param text     parte do código, do pedido, do e-mail ou do nome do comprador ou de quem vai usar
	 */
	List<MatchdayTicketModel> search(String gameCode, String sector, String status, String text);

	MatchdayTicketModel find(String code);

	/** Histórico do ingresso, mais novo primeiro */
	List<MatchdayTicketEventModel> history(MatchdayTicketModel ticket);

	/** Poltronas livres do mesmo jogo e setor (para a troca) */
	List<ProductModel> freeSeats(MatchdayTicketModel ticket);

	/** Convidados ativos do comprador (para trocar quem vai usar) */
	List<SocioGuestModel> guests(MatchdayTicketModel ticket);

	/** Valor que volta se cancelar agora (null = não pode cancelar agora; o motivo vem em {@link #cancelBlock}) */
	Double refundQuote(MatchdayTicketModel ticket, Date now);

	/** Por que não pode cancelar agora (null = pode) */
	String cancelBlock(MatchdayTicketModel ticket, Date now);

	MatchdayTicketModel changeSeat(String code, String newSeatCode, String author);

	/** Troca quem vai usar: convidado do comprador (guestPk) ou um nome livre (holder); os dois vazios = o titular */
	MatchdayTicketModel changeHolder(String code, String guestPk, String holder, String author);

	MatchdayTicketModel cancel(String code, String reason, String author);

	MatchdayTicketModel markUsed(String code, String author);

	MatchdayTicketModel addNote(String code, String note, String author);

	/**
	 * Ação pedida por fora (Integration API, Claude do gestor de jogos): o MatchdayTicketEvent novo
	 * com ticket, type (TROCA_POLTRONA, TROCA_TITULAR, CANCELAMENTO, UTILIZACAO ou OBSERVACAO),
	 * toValue (nova poltrona ou quem vai usar) e note (motivo ou observação) é executado com as
	 * mesmas regras e vira o próprio registro no histórico. Chamado pelo interceptor do evento.
	 *
	 * @throws MatchdayTicketException com o motivo da recusa
	 */
	void apply(MatchdayTicketEventModel command, InterceptorContext ctx);

	/** true enquanto o serviço grava os próprios eventos (o interceptor deixa passar) */
	boolean isInternal();

	/** Ingresso cancelado desta linha de pedido (para os pontos do sócio) */
	boolean isCancelled(String orderCode, Integer entryNumber);
}
