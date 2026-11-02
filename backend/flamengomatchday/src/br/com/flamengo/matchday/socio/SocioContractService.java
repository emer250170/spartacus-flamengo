package br.com.flamengo.matchday.socio;

import br.com.flamengo.matchday.model.SocioContractModel;
import br.com.flamengo.matchday.model.SocioEventModel;
import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.core.model.user.CustomerModel;

import java.util.List;


/**
 * Vigência do sócio (item 6 do plano de evolução): contrato de 12 meses com
 * renovação automática.
 * <ul>
 * <li>o sócio desliga a renovação ou agenda a mudança para um plano menor até
 * {@value #DEFAULT_DEADLINE} dias antes do fim (propriedade {@value #DEADLINE_PROPERTY});</li>
 * <li>upgrade (pedido novo) recomeça o contrato;</li>
 * <li>a virada (Backoffice ou Claude) simula o aniversário: novo período de 12
 * meses a partir de hoje com a mudança agendada; com a renovação desligada,
 * encerra sem multa.</li>
 * </ul>
 * Sem contrato gravado, a vigência sai do pedido mais novo do plano (como antes).
 * Chave {@value #ENABLED_PROPERTY}.
 */
public interface SocioContractService
{
	String ENABLED_PROPERTY = "flamengo.socio.vigencia.ativo";
	String DEADLINE_PROPERTY = "flamengo.socio.vigencia.prazoRenovacao.dias";
	int DEFAULT_DEADLINE = 30;
	int TERM_MONTHS = 12;
	/** Motivo gravado nos convidados encerrados pela virada */
	String END_BY_EXPIRY = "Fim da vigência";
	String END_BY_DOWNGRADE = "Mudança de plano na renovação";

	boolean isEnabled();

	/** Vigência atual do torcedor (gravada ou calculada do pedido) */
	SocioContractStatus status(CustomerModel customer);

	/** Liga ou desliga a renovação automática (até o prazo); grava evento. Mensagem para o usuário. */
	String setAutoRenew(CustomerModel customer, boolean autoRenew, String origin);

	/** Agenda a mudança para um plano menor na próxima vigência (null cancela); grava evento. */
	String scheduleChange(CustomerModel customer, String planCode, String origin);

	/**
	 * Virada da vigência no evento informado (RENOVACAO): renova por 12 meses a
	 * partir de hoje com a mudança agendada, ou encerra sem multa (o evento vira
	 * FIM_VIGENCIA) se a renovação está desligada. Preenche tipo, plano e
	 * resultado do evento e devolve o que precisa ser salvo junto (torcedor,
	 * contrato, convidados). Não salva.
	 */
	List<ItemModel> turn(CustomerModel customer, SocioEventModel event);

	/** Virada com evento novo, tudo salvo (Backoffice). Mensagem para o usuário. */
	String turnNow(CustomerModel customer, String origin);

	/**
	 * Adesão ou upgrade (evento ADESAO do pedido): recomeça o contrato gravado no
	 * plano comprado. Devolve o contrato a salvar (null se não há contrato gravado).
	 */
	SocioContractModel onJoin(SocioEventModel joinEvent);
}
