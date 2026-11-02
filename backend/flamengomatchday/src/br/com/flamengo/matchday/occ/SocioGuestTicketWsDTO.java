package br.com.flamengo.matchday.occ;

import java.io.Serializable;


/**
 * Ingresso de convidado na API (Meus convidados e jornada de matchday). Na entrada
 * (PUT /socioguests/tickets) vale cartId, seatCode e guestId (vazio = o titular).
 */
public class SocioGuestTicketWsDTO implements Serializable
{
	private static final long serialVersionUID = 1L;

	private String guestId;
	private String holder;
	private String seatCode;
	private String seatName;
	private String gameCode;
	private String gameName;
	private String kickoff;
	private String orderCode;
	private String cartId;

	public String getGuestId()
	{
		return guestId;
	}

	public void setGuestId(final String guestId)
	{
		this.guestId = guestId;
	}

	public String getHolder()
	{
		return holder;
	}

	public void setHolder(final String holder)
	{
		this.holder = holder;
	}

	public String getSeatCode()
	{
		return seatCode;
	}

	public void setSeatCode(final String seatCode)
	{
		this.seatCode = seatCode;
	}

	public String getSeatName()
	{
		return seatName;
	}

	public void setSeatName(final String seatName)
	{
		this.seatName = seatName;
	}

	public String getGameCode()
	{
		return gameCode;
	}

	public void setGameCode(final String gameCode)
	{
		this.gameCode = gameCode;
	}

	public String getGameName()
	{
		return gameName;
	}

	public void setGameName(final String gameName)
	{
		this.gameName = gameName;
	}

	public String getKickoff()
	{
		return kickoff;
	}

	public void setKickoff(final String kickoff)
	{
		this.kickoff = kickoff;
	}

	public String getOrderCode()
	{
		return orderCode;
	}

	public void setOrderCode(final String orderCode)
	{
		this.orderCode = orderCode;
	}

	public String getCartId()
	{
		return cartId;
	}

	public void setCartId(final String cartId)
	{
		this.cartId = cartId;
	}
}
