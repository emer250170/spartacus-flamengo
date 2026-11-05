package br.com.flamengo.matchday.socio;

import br.com.flamengo.matchday.model.SocioGuestModel;

import java.util.ArrayList;
import java.util.List;


/** Situação dos convidados de um titular ({@link SocioGuestService#overview}). */
public class SocioGuestOverview
{
	/** Chave flamengo.socio.convidados.ativo */
	public boolean enabled;
	/** Plano atual do titular (null se não é sócio) */
	public String planCode;
	public String planName;
	/** Máximo de convidados do plano */
	public int planMaxGuests;
	/** Vagas de convidado contratadas no pedido do plano */
	public int contracted;
	/** Mensalidade de cada convidado (R$), já cobrada no plano */
	public double guestPrice;
	/** Benefícios suspensos por mensalidade em atraso */
	public boolean suspended;
	/** Pode cadastrar mais um convidado agora */
	public boolean canAdd;
	/** Por que não pode cadastrar (null quando pode) */
	public String reason;
	/** Plano ainda só no carrinho (04/11): as vagas vêm do carrinho e o convidado cadastrado fica preso a ele */
	public boolean planInCart;
	/** Carrinho do plano (só com planInCart) */
	public transient String cartCode;
	/** Plano que vale para os convidados (comprado ou no carrinho) */
	public transient de.hybris.platform.core.model.ItemModel planModel;
	public final List<SocioGuestModel> active = new ArrayList<>();
	/** Encerrados, os mais novos primeiro (até 10) */
	public final List<SocioGuestModel> ended = new ArrayList<>();
	/** Ingressos dos convidados para jogos ainda não realizados (pedidos e carrinho) */
	public final List<SocioGuestTicket> tickets = new ArrayList<>();
}
