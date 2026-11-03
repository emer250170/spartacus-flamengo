package br.com.flamengo.matchday.socio;

import br.com.flamengo.matchday.model.SocioExperienceModel;
import br.com.flamengo.matchday.model.SocioRedemptionModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.ordersplitting.model.StockLevelModel;

import java.util.List;


/**
 * Experiências do sócio vendidas na loja (item 9 do plano de evolução).
 * <p>
 * Experiência com preço vira produto da loja ao salvar (catálogo Online, categoria
 * "Experiências", preço, estoque). As vagas são uma só: o estoque do produto é a quantidade
 * da experiência menos os vouchers válidos (resgates com pontos e compras). Ao fechar o
 * pedido, cada unidade comprada vira um voucher NACAO-… com origem "Compra".
 * <p>
 * Chave: flamengo.socio.experiencias.venda.ativo (false = nada vai para a loja; o resgate
 * com pontos continua).
 */
public interface SocioExperienceService
{
	String SALE_ENABLED_PROPERTY = "flamengo.socio.experiencias.venda.ativo";
	String CATEGORY = "experiencias";
	String PRODUCT_PREFIX = "EXP-";
	String ORIGIN_PURCHASE = "Compra";

	boolean isSaleEnabled();

	/**
	 * Cria ou atualiza o produto da experiência (ou tira da loja, sem preço ou inativa).
	 * Chamado pelo interceptor ao salvar; grava {@code product} na experiência (sem salvar).
	 *
	 * @return mensagem para o gestor (statusMessage)
	 */
	String publish(SocioExperienceModel experience);

	/** Vagas que sobram (null = sem limite), com {@code delta} somado (ex.: +1 ao cancelar um voucher ainda não salvo) */
	Integer remaining(SocioExperienceModel experience, int delta);

	/** Estoque do produto ajustado às vagas que sobram, sem salvar (null = experiência sem produto) */
	StockLevelModel adjustStock(SocioExperienceModel experience, int delta);

	/** Ajusta e salva o estoque do produto */
	void syncStock(SocioExperienceModel experience);

	/** Experiência ligada ao produto (null = não é experiência) */
	SocioExperienceModel findByProduct(ProductModel product);

	/** Gera os vouchers das experiências compradas no pedido (uma vez por pedido) */
	List<SocioRedemptionModel> recordPurchase(AbstractOrderModel order);

	/** Voucher pelo código (null = não existe) */
	SocioRedemptionModel findVoucher(String voucher);

	/**
	 * Marca o voucher como utilizado (portaria ou atendimento). Recusa voucher cancelado ou já usado.
	 *
	 * @throws SocioPointsException com a mensagem para o gestor
	 */
	SocioRedemptionModel useVoucher(String voucher, String author);

	/**
	 * Cancela o voucher: a vaga volta e, no resgate, os pontos também. Compra: o reembolso é
	 * feito pelo atendimento (fica registrado no motivo). Recusa voucher já usado.
	 *
	 * @throws SocioPointsException com a mensagem para o gestor
	 */
	SocioRedemptionModel cancelVoucher(String voucher, String author, String reason);

	// ------------------------------------------------------------ cockpit Gestão de experiências

	/** Todas as experiências (também as inativas), na ordem da vitrine */
	List<SocioExperienceModel> allExperiences();

	/** Cards e relatório: números por experiência e pelos últimos {@code months} meses */
	SocioExperienceOverview overview(int months);

	/**
	 * Resgates e compras para a lista do cockpit, mais novos primeiro.
	 *
	 * @param experienceCode vazio = todas
	 * @param status         VALID (válido, não usado), USED, CANCELLED ou vazio = todos
	 * @param origin         PURCHASE (compra), POINTS (resgate com pontos) ou vazio = todos
	 * @param text           parte do voucher, do e-mail ou do nome do torcedor (vazio = todos)
	 */
	List<SocioRedemptionModel> searchRedemptions(String experienceCode, String status, String origin, String text);
}
