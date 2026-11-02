package br.com.flamengo.matchday.socio;

import java.util.Date;


/** Situação do Termo do Nação para um torcedor ({@link SocioTermService#status}). */
public class SocioTermStatus
{
	/** Chave flamengo.socio.termo.ativo */
	public boolean enabled;
	/** O torcedor é sócio (plano comprado) */
	public boolean member;
	/** Versão atual (null: nenhuma publicada) */
	public String version;
	public String title;
	public String text;
	/** O que mudou na versão atual */
	public String summary;
	public Date publishedAt;
	/** Aceitou a versão atual */
	public boolean accepted;
	/** Último aceite (qualquer versão) */
	public String acceptedVersion;
	public Date acceptedAt;
	public String acceptedOrigin;
	public String acceptedOrder;
}
