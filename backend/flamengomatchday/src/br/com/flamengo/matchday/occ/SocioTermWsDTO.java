package br.com.flamengo.matchday.occ;

import java.io.Serializable;


/** Termo do Nação na API de comércio: versão atual, texto e o aceite do torcedor. */
public class SocioTermWsDTO implements Serializable
{
	private static final long serialVersionUID = 1L;

	private Boolean enabled;
	private Boolean member;
	private String version;
	private String title;
	private String text;
	private String summary;
	private String publishedAt;
	private Boolean accepted;
	private String acceptedVersion;
	private String acceptedAt;
	private String acceptedOrigin;
	private String acceptedOrder;
	/** Só no POST: carrinho do checkout (vazio = área do sócio) */
	private String cartId;

	public Boolean getEnabled()
	{
		return enabled;
	}

	public void setEnabled(final Boolean enabled)
	{
		this.enabled = enabled;
	}

	public Boolean getMember()
	{
		return member;
	}

	public void setMember(final Boolean member)
	{
		this.member = member;
	}

	public String getVersion()
	{
		return version;
	}

	public void setVersion(final String version)
	{
		this.version = version;
	}

	public String getTitle()
	{
		return title;
	}

	public void setTitle(final String title)
	{
		this.title = title;
	}

	public String getText()
	{
		return text;
	}

	public void setText(final String text)
	{
		this.text = text;
	}

	public String getSummary()
	{
		return summary;
	}

	public void setSummary(final String summary)
	{
		this.summary = summary;
	}

	public String getPublishedAt()
	{
		return publishedAt;
	}

	public void setPublishedAt(final String publishedAt)
	{
		this.publishedAt = publishedAt;
	}

	public Boolean getAccepted()
	{
		return accepted;
	}

	public void setAccepted(final Boolean accepted)
	{
		this.accepted = accepted;
	}

	public String getAcceptedVersion()
	{
		return acceptedVersion;
	}

	public void setAcceptedVersion(final String acceptedVersion)
	{
		this.acceptedVersion = acceptedVersion;
	}

	public String getAcceptedAt()
	{
		return acceptedAt;
	}

	public void setAcceptedAt(final String acceptedAt)
	{
		this.acceptedAt = acceptedAt;
	}

	public String getAcceptedOrigin()
	{
		return acceptedOrigin;
	}

	public void setAcceptedOrigin(final String acceptedOrigin)
	{
		this.acceptedOrigin = acceptedOrigin;
	}

	public String getAcceptedOrder()
	{
		return acceptedOrder;
	}

	public void setAcceptedOrder(final String acceptedOrder)
	{
		this.acceptedOrder = acceptedOrder;
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
