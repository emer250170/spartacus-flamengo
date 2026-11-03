package br.com.flamengo.matchday.constants;

/**
 * Constantes da extensao flamengomatchday.
 */
public final class FlamengomatchdayConstants extends GeneratedFlamengomatchdayConstants
{
	public static final String EXTENSIONNAME = "flamengomatchday";

	/** Marca gravada em Order.statusInfo quando o estoque do pedido ja foi baixado */
	public static final String STOCK_MARKER = "matchday:estoque-baixado";

	public static final String HOLD_MINUTES = "flamengomatchday.seathold.minutes";
	public static final String SEAT_CODE_PREFIX = "flamengomatchday.seat.codeprefix";
	public static final String WAREHOUSE = "flamengomatchday.warehouse";
	/** Retirada na loja física (item 12): false = linha de retirada baixa o estoque como as outras */
	public static final String PICKUP_ENABLED = "flamengo.retirada.ativo";
	/** Operação do pedido da loja (item 14): entrega em casa com remessa do depósito da loja online */
	public static final String STORE_OPERATION_ENABLED = "flamengo.loja.operacao.ativo";
	public static final String STORE_OPERATION_WAREHOUSE = "flamengo.loja.operacao.deposito";
	public static final String STORE_OPERATION_NO_CONSIGNMENT = "flamengo.loja.operacao.semRemessa";
	/** Chave do pagamento do item 7, que a operação do pedido também exige */
	public static final String PAYMENT_ENABLED = "flamengo.socio.pagamento.ativo";

	private FlamengomatchdayConstants()
	{
		// so constantes
	}
}
