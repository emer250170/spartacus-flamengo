package br.com.flamengo.matchday.socio;

import br.com.flamengo.matchday.model.SocioPlanModel;

import java.util.ArrayList;
import java.util.List;


/**
 * Resultado da conferência de elegibilidade de um torcedor para comprar ingresso
 * de um jogo ({@link SocioEligibilityService#evaluate}).
 */
public class SocioEligibility
{
	/** Todas as condições atendidas */
	public boolean eligible = true;
	/** Motivos da negativa, em português, para o torcedor e o atendimento */
	public final List<String> reasons = new ArrayList<>();
	/** Todas as condições conferidas ("OK ...", "NÃO ...", "AVISO ...") */
	public final List<String> checks = new ArrayList<>();

	/** Plano considerado (inclui plano no carrinho); null se não é sócio */
	public SocioPlanModel plan;
	/** O plano ainda está só no carrinho */
	public boolean planInCart;
	/** Benefícios suspensos por mensalidade em atraso */
	public boolean suspended;
	/** Onda do torcedor (4 = venda geral) */
	public int memberWave;
	/** Onda aberta no jogo agora (0 = nenhuma; 4 = venda geral) */
	public int openWave;
	/** Biometria facial validada */
	public boolean biometry;
	/** A biometria é exigida (chave flamengo.socio.biometria.exigida) */
	public boolean biometryRequired;
	/** Ingressos do jogo nos pedidos do torcedor */
	public int ticketsOrdered;
	/** Ingressos do jogo no carrinho (fora a poltrona que está entrando) */
	public int ticketsInCart;
	/** Limite de ingressos por jogo */
	public int ticketLimit;

	public void deny(final String reason)
	{
		eligible = false;
		reasons.add(reason);
		checks.add("NÃO " + reason);
	}

	public void ok(final String text)
	{
		checks.add("OK " + text);
	}

	public void warn(final String text)
	{
		checks.add("AVISO " + text);
	}
}
