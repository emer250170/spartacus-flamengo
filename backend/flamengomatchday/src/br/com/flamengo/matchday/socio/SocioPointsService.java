package br.com.flamengo.matchday.socio;

import br.com.flamengo.matchday.model.SocioRedemptionModel;
import de.hybris.platform.core.model.user.CustomerModel;


/**
 * Pontos, experiências e resgates do sócio-torcedor (item 8 do plano de evolução).
 * <p>
 * Ganho: cada ingresso dá {@code flamengo.socio.pontos.porIngresso} pontos (padrão 100) e
 * cada real em produtos dá {@code flamengo.socio.pontos.porReal} (padrão 1; plano e
 * convidados não contam). Contam os pedidos fechados desde a adesão (Pix ou boleto
 * aguardando e pedidos cancelados não contam), mais um bônus de adesão
 * ({@code flamengo.socio.pontos.bonusAdesao}, padrão 500). Igual para todos os planos.
 * <p>
 * Resgate: só sócio em dia (nem suspenso, nem aguardando pagamento, nem ex-sócio),
 * experiência ativa, plano com mensalidade igual ou maior que a do plano mínimo, vaga
 * sobrando e saldo suficiente. Gera um voucher ({@code SocioRedemption}) e o evento
 * "Resgate de pontos" na linha do tempo. Resgate cancelado no Backoffice devolve
 * pontos e vaga (o saldo é sempre calculado, nada é gravado no torcedor).
 * <p>
 * Chave: flamengo.socio.pontos.ativo (false = sem pontos na loja, na ficha e no Claude).
 */
public interface SocioPointsService
{
	String ENABLED_PROPERTY = "flamengo.socio.pontos.ativo";
	String PER_TICKET_PROPERTY = "flamengo.socio.pontos.porIngresso";
	String PER_REAL_PROPERTY = "flamengo.socio.pontos.porReal";
	String BONUS_PROPERTY = "flamengo.socio.pontos.bonusAdesao";

	boolean isEnabled();

	/** Saldo, ganhos, resgates e vitrine para o torcedor */
	SocioPoints status(CustomerModel customer);

	/**
	 * Resgata a experiência com os pontos do torcedor.
	 *
	 * @param origin
	 *           Loja ou Claude
	 * @throws SocioPointsException
	 *            com o motivo da recusa
	 */
	SocioRedemptionModel redeem(CustomerModel customer, String experienceCode, String origin);
}
