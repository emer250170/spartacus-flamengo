package br.com.flamengo.matchday.socio;

import java.util.List;


/**
 * Atendimento do sócio no Backoffice (Sócio-Torcedor › Sócios): busca, ficha e
 * ações sobre o plano do torcedor. As ações gravam eventos na linha do tempo
 * (SocioEvent) com o usuário da sessão como autor.
 */
public interface SocioMemberService
{
	/**
	 * Sócios e ex-sócios.
	 *
	 * @param text
	 *           parte do nome ou do e-mail (vazio = todos)
	 * @param planCode
	 *           código do plano (vazio = todos)
	 * @param status
	 *           situação (null = todas)
	 * @return até 200, por nome
	 */
	List<SocioMember> search(String text, String planCode, SocioMember.Status status);

	/** Ficha completa (null se o torcedor não existe) */
	SocioMember detail(String uid);

	/** Simula mensalidade em atraso: Suspensão. @return resultado para mostrar */
	String suspend(String uid, String note);

	/** Pagamento confirmado: Regularização. @return resultado para mostrar */
	String regularize(String uid, String note);

	/** Observação na linha do tempo. @return resultado para mostrar */
	String addNote(String uid, String note);

	/**
	 * Confirma o pedido de cancelamento aberto: fecha o chamado e o torcedor deixa
	 * de ser sócio (SocioCancelTicketInterceptor). @return resultado para mostrar
	 */
	String confirmCancellation(String uid);

	/**
	 * Biometria facial validada ou pendente (grupo biometria-validada) e observação
	 * na linha do tempo. Vale também para quem não é sócio. @return resultado para mostrar
	 */
	String setBiometry(String uid, boolean validated, String note);

	/**
	 * Vira a vigência agora (item 6): renova por 12 meses a partir de hoje com a
	 * mudança de plano agendada, ou encerra sem multa se a renovação está
	 * desligada. @return resultado para mostrar
	 */
	String turnContract(String uid);

	/**
	 * Confirma o Pix ou boleto aguardando pagamento (item 7): o torcedor vira sócio do
	 * plano do pedido (evento Pagamento confirmado). @return resultado para mostrar
	 */
	String confirmPayment(String uid);
}
