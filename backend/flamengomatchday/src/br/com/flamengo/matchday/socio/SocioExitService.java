package br.com.flamengo.matchday.socio;

import br.com.flamengo.matchday.model.SocioEventModel;
import de.hybris.platform.core.model.user.CustomerModel;

import java.util.Date;


/**
 * Regras de saída do sócio (item 5 do plano de evolução):
 * <ul>
 * <li>dentro do prazo de arrependimento (7 dias corridos da adesão ao plano atual):
 * reembolso do que foi pago na adesão (plano + convidados);</li>
 * <li>fora do prazo: multa de 10% sobre as mensalidades restantes do contrato de
 * 12 meses (plano + convidados).</li>
 * </ul>
 * Vale o dia do pedido de cancelamento (abertura do chamado): o valor que a loja
 * mostra ao pedir é o que o atendimento confirma. Prazo e percentual em
 * propriedades; chave liga/desliga {@value #ENABLED_PROPERTY}.
 */
public interface SocioExitService
{
	String ENABLED_PROPERTY = "flamengo.socio.saida.ativo";
	String DAYS_PROPERTY = "flamengo.socio.saida.arrependimento.dias";
	String FINE_PROPERTY = "flamengo.socio.saida.multa.percentual";
	int DEFAULT_DAYS = 7;
	double DEFAULT_FINE = 10;
	/** Mensalidades do contrato anual */
	int INSTALLMENTS = 12;

	boolean isEnabled();

	/**
	 * Simulação para o sócio com a data-base informada.
	 *
	 * @return null se o torcedor não é sócio (ou não há pedido do plano)
	 */
	SocioExitQuote quote(CustomerModel customer, Date basis);

	/**
	 * Simulação de agora: com chamado de cancelamento aberto, a data-base é a
	 * abertura do chamado; sem, é agora. Null se não é sócio.
	 */
	SocioExitQuote currentQuote(CustomerModel customer);

	/** Valor do último cancelamento confirmado (evento CANCELAMENTO com valores) ou null */
	SocioExitQuote lastSettlement(CustomerModel customer);

	/**
	 * Grava os valores no evento de cancelamento (reembolso ou multa) e devolve o
	 * texto para o resultado do evento ("reembolso de R$ 67,10 (arrependimento)").
	 */
	String apply(SocioEventModel event, SocioExitQuote quote);

	/** Uma linha em português: "Reembolso de R$ 67,10 (arrependimento até 09/11/2026)" */
	String describe(SocioExitQuote quote);
}
