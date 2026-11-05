package br.com.flamengo.matchday.ticket;

import br.com.flamengo.matchday.socio.SocioGuestException;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.user.CustomerModel;

import java.util.Date;
import java.util.List;


/**
 * Ingressos adicionais de quem não é sócio (04/11): o torcedor escolhe várias
 * poltronas do mesmo jogo (até flamengo.ingressos.limite.geral) e, a partir da
 * segunda, informa nome, CPF, nascimento e e-mail de quem vai usar cada uma. A
 * primeira poltrona de cada jogo fica no nome do comprador. Os dados ficam na linha
 * do carrinho (ticketHolder*), passam para o pedido pelo clone padrão e daí para o
 * ingresso. Chave flamengo.ingressos.portador.obrigatorio (true): com false, o pedido
 * fecha sem os dados, como antes.
 */
public interface TicketHolderService
{
	String REQUIRED_PROPERTY = "flamengo.ingressos.portador.obrigatorio";
	String ENTRY_CPF = "ticketHolderCpf";
	String ENTRY_BIRTH_DATE = "ticketHolderBirthDate";
	String ENTRY_EMAIL = "ticketHolderEmail";

	/** Poltronas de jogo do carrinho do torcedor, com quem vai usar */
	List<TicketHolder> holders(CustomerModel owner, String cartCode) throws SocioGuestException;

	/** Grava quem vai usar a poltrona do carrinho (nome vazio = volta para o comprador) */
	void setHolder(CustomerModel owner, String cartCode, String seatCode, String name, String cpf, Date birthDate, String email)
			throws SocioGuestException;

	/** Ao fechar o pedido de quem não é sócio: todo ingresso adicional com os dados de quem vai usar */
	void checkCart(CartModel cart) throws SocioGuestException;

	/** CPF, nascimento e e-mail gravados na linha (null quando não há) */
	String cpfOf(AbstractOrderEntryModel entry);

	Date birthDateOf(AbstractOrderEntryModel entry);

	String emailOf(AbstractOrderEntryModel entry);
}
