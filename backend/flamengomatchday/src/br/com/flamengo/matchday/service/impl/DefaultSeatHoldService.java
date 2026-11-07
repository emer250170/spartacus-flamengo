package br.com.flamengo.matchday.service.impl;

import br.com.flamengo.matchday.constants.FlamengomatchdayConstants;
import br.com.flamengo.matchday.model.SeatHoldModel;
import br.com.flamengo.matchday.service.SeatHoldRemovalContext;
import br.com.flamengo.matchday.service.SeatHoldService;
import de.hybris.platform.commerceservices.order.CommerceCartModificationException;
import de.hybris.platform.commerceservices.order.CommerceCartService;
import de.hybris.platform.commerceservices.service.data.CommerceCartParameter;
import de.hybris.platform.core.PK;
import de.hybris.platform.core.Registry;
import de.hybris.platform.core.Tenant;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.delivery.DeliveryModeModel;
import de.hybris.platform.commerceservices.model.PickUpDeliveryModeModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.ordersplitting.model.StockLevelModel;
import de.hybris.platform.ordersplitting.model.WarehouseModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.i18n.CommonI18NService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.session.SessionExecutionBody;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.site.BaseSiteService;
import de.hybris.platform.servicelayer.user.UserService;
import de.hybris.platform.stock.StockService;
import de.hybris.platform.stock.exception.InsufficientStockLevelException;
import de.hybris.platform.variants.model.VariantProductModel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DefaultSeatHoldService implements SeatHoldService
{
	/** Marca do pedido recusado antes do pagamento, depois de devolver o estoque (07/11) */
	public static final String STOCK_RETURNED_MARKER = "matchday:estoque-devolvido";

	private static final Logger LOG = LoggerFactory.getLogger(DefaultSeatHoldService.class);

	private static final String FIND_BY_PRODUCT = "SELECT {" + SeatHoldModel.PK + "} FROM {" + SeatHoldModel._TYPECODE
			+ "} WHERE {" + SeatHoldModel.PRODUCT + "} = ?product";
	private static final String FIND_EXPIRED = "SELECT {" + SeatHoldModel.PK + "} FROM {" + SeatHoldModel._TYPECODE
			+ "} WHERE {" + SeatHoldModel.EXPIRESAT + "} < ?now";
	private static final String FIND_CART = "SELECT {" + CartModel.PK + "} FROM {" + CartModel._TYPECODE + "} WHERE {"
			+ CartModel.CODE + "} = ?code";
	/** Espera a remocao feita no Backoffice ser gravada antes de mexer no carrinho */
	private static final long EXTERNAL_REMOVAL_DELAY_MS = 2000L;

	private ModelService modelService;
	private FlexibleSearchService flexibleSearchService;
	private StockService stockService;
	private UserService userService;
	private ConfigurationService configurationService;
	private CommerceCartService commerceCartService;
	private SessionService sessionService;
	private CommonI18NService commonI18NService;
	private BaseSiteService baseSiteService;

	@Override
	public boolean isSeat(final ProductModel product)
	{
		if (!(product instanceof VariantProductModel) || product.getCode() == null)
		{
			return false;
		}
		final String prefix = configurationService.getConfiguration().getString(FlamengomatchdayConstants.SEAT_CODE_PREFIX,
				"JOGO-");
		return product.getCode().startsWith(prefix);
	}

	@Override
	public boolean hold(final ProductModel seat, final AbstractOrderModel cart, final boolean renew)
	{
		final SeatHoldModel existing = findHold(seat);
		if (existing != null)
		{
			if (isOwner(existing, cart))
			{
				existing.setCartCode(cart.getCode());
				existing.setUser(cart.getUser());
				if (renew)
				{
					existing.setExpiresAt(newExpiry());
				}
				modelService.save(existing);
				return true;
			}
			if (existing.getExpiresAt() != null && existing.getExpiresAt().after(new Date()))
			{
				return false;
			}
			// a reserva de outra pessoa venceu: solta antes de tentar reservar
			releaseHold(existing);
		}

		final StockLevelModel level = findStockLevel(seat);
		if (level == null)
		{
			return false;
		}
		final WarehouseModel warehouse = level.getWarehouse();
		try
		{
			// atomico no banco: so passa se ainda houver unidade livre
			stockService.reserve(seat, warehouse, 1, "matchday:reserva " + cart.getCode());
		}
		catch (final InsufficientStockLevelException e)
		{
			return false;
		}

		final SeatHoldModel hold = modelService.create(SeatHoldModel.class);
		hold.setProduct(seat);
		hold.setWarehouse(warehouse);
		hold.setCartCode(cart.getCode());
		hold.setUser(cart.getUser());
		hold.setExpiresAt(newExpiry());
		try
		{
			modelService.save(hold);
		}
		catch (final RuntimeException e)
		{
			LOG.warn("N\u00e3o foi poss\u00edvel gravar a reserva de {}: {}", seat.getCode(), e.getMessage());
			stockService.release(seat, warehouse, 1, "matchday:reserva desfeita " + cart.getCode());
			return false;
		}
		LOG.info("Poltrona {} reservada para o carrinho {}", seat.getCode(), cart.getCode());
		return true;
	}

	@Override
	public void releaseIfHeldElsewhereBySameUser(final ProductModel seat, final AbstractOrderModel cart)
	{
		final SeatHoldModel existing = findHold(seat);
		if (existing != null && !cart.getCode().equals(existing.getCartCode()) && isSameRegisteredUser(existing.getUser(),
				cart.getUser()))
		{
			releaseHold(existing);
		}
	}

	@Override
	public void release(final ProductModel seat, final AbstractOrderModel cart)
	{
		final SeatHoldModel existing = findHold(seat);
		if (existing != null && cart.getCode().equals(existing.getCartCode()))
		{
			releaseHold(existing);
		}
	}

	@Override
	public void commitSale(final AbstractOrderModel order)
	{
		if (FlamengomatchdayConstants.STOCK_MARKER.equals(order.getStatusInfo()))
		{
			return;
		}
		final boolean homeDelivery = shipsFromStoreWarehouse(order);
		for (final AbstractOrderEntryModel entry : order.getEntries())
		{
			final ProductModel product = entry.getProduct();
			final int quantity = entry.getQuantity() == null ? 0 : entry.getQuantity().intValue();
			if (product == null || quantity <= 0)
			{
				continue;
			}
			if (entry.getDeliveryPointOfService() != null
					&& configurationService.getConfiguration().getBoolean(FlamengomatchdayConstants.PICKUP_ENABLED, true))
			{
				// retirada na loja (item 12): o estoque é o da loja, reservado pela remessa do OMS
				// (passo sourcePickup do order-process), e não o do depósito da loja online
				continue;
			}
			final SeatHoldModel hold = isSeat(product) ? findHold(product) : null;
			final StockLevelModel level = hold != null ? stockService.getStockLevel(product, hold.getWarehouse())
					: findStockLevel(product);
			if (level == null)
			{
				continue;
			}
			if (hold == null && entry.getDeliveryPointOfService() == null && homeDelivery && hasConsignment(product, level))
			{
				// entrega em casa (item 14): o estoque é reservado pela remessa do warehouse_s, criada pelo
				// order-process depois da análise de fraude e do pagamento (passo sourceFulfillment)
				continue;
			}
			if (hold != null)
			{
				// a unidade sai de "reservada" e vira venda
				SeatHoldRemovalContext.runInternal(() -> modelService.remove(hold));
				stockService.release(product, level.getWarehouse(), quantity, "matchday:venda " + order.getCode());
			}
			modelService.refresh(level);
			level.setAvailable(Math.max(0, level.getAvailable() - quantity));
			modelService.save(level);
		}
		order.setStatusInfo(FlamengomatchdayConstants.STOCK_MARKER);
		modelService.save(order);
		LOG.info("Estoque baixado para o pedido {}", order.getCode());
	}

	@Override
	public void revertSale(final AbstractOrderModel order)
	{
		if (order == null || !FlamengomatchdayConstants.STOCK_MARKER.equals(order.getStatusInfo()))
		{
			return;
		}
		final boolean homeDelivery = shipsFromStoreWarehouse(order);
		for (final AbstractOrderEntryModel entry : order.getEntries())
		{
			final ProductModel product = entry.getProduct();
			final int quantity = entry.getQuantity() == null ? 0 : entry.getQuantity().intValue();
			if (product == null || quantity <= 0)
			{
				continue;
			}
			// as mesmas regras do commitSale: retirada na loja e entrega em casa com remessa não baixaram aqui
			if (entry.getDeliveryPointOfService() != null
					&& configurationService.getConfiguration().getBoolean(FlamengomatchdayConstants.PICKUP_ENABLED, true))
			{
				continue;
			}
			final StockLevelModel level = findStockLevel(product);
			if (level == null)
			{
				continue;
			}
			if (!isSeat(product) && entry.getDeliveryPointOfService() == null && homeDelivery && hasConsignment(product, level))
			{
				continue;
			}
			modelService.refresh(level);
			level.setAvailable(level.getAvailable() + quantity);
			modelService.save(level);
		}
		order.setStatusInfo(STOCK_RETURNED_MARKER);
		modelService.save(order);
		LOG.info("Estoque devolvido para o pedido {}", order.getCode());
	}

	/**
	 * Item 14 (chave flamengo.loja.operacao.ativo): o pedido tem entrega em casa, então as linhas
	 * físicas viram remessa do depósito da loja online no order-process.
	 */
	protected boolean shipsFromStoreWarehouse(final AbstractOrderModel order)
	{
		final org.apache.commons.configuration.Configuration config = configurationService.getConfiguration();
		if (!config.getBoolean(FlamengomatchdayConstants.STORE_OPERATION_ENABLED, false)
				|| !config.getBoolean(FlamengomatchdayConstants.PAYMENT_ENABLED, false))
		{
			return false;
		}
		final DeliveryModeModel mode = order.getDeliveryMode();
		return mode != null && !(mode instanceof PickUpDeliveryModeModel) && !"matchday-digital".equals(mode.getCode());
	}

	/** Mesma regra do passo sourceFulfillment: estoque no depósito da loja online e código fora dos prefixos sem remessa */
	protected boolean hasConsignment(final ProductModel product, final StockLevelModel level)
	{
		final org.apache.commons.configuration.Configuration config = configurationService.getConfiguration();
		final String warehouse = config.getString(FlamengomatchdayConstants.STORE_OPERATION_WAREHOUSE, "warehouse_s");
		if (level.getWarehouse() == null || !warehouse.equals(level.getWarehouse().getCode()))
		{
			return false;
		}
		for (final String prefix : config.getString(FlamengomatchdayConstants.STORE_OPERATION_NO_CONSIGNMENT, "").split(","))
		{
			if (!prefix.trim().isEmpty() && product.getCode().startsWith(prefix.trim()))
			{
				return false;
			}
		}
		return true;
	}

	@Override
	public int releaseExpired()
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery(FIND_EXPIRED);
		query.addQueryParameter("now", new Date());
		final List<SeatHoldModel> expired = new ArrayList<>(flexibleSearchService.<SeatHoldModel> search(query).getResult());
		int released = 0;
		for (final SeatHoldModel hold : expired)
		{
			if (releaseHold(hold))
			{
				released++;
			}
		}
		return released;
	}

	protected boolean releaseHold(final SeatHoldModel hold)
	{
		final ProductModel product = hold.getProduct();
		final WarehouseModel warehouse = hold.getWarehouse();
		final String cartCode = hold.getCartCode();
		try
		{
			// apaga primeiro: se duas rotinas tentarem soltar a mesma reserva, so uma devolve a unidade
			SeatHoldRemovalContext.runInternal(() -> modelService.remove(hold));
		}
		catch (final RuntimeException e)
		{
			LOG.debug("Reserva j\u00e1 liberada: {}", e.getMessage());
			return false;
		}
		if (product != null && warehouse != null)
		{
			final StockLevelModel level = stockService.getStockLevel(product, warehouse);
			if (level != null)
			{
				modelService.refresh(level);
				if (level.getReserved() > 0)
				{
					stockService.release(product, warehouse, 1, "matchday:liberada " + cartCode);
				}
			}
			LOG.info("Poltrona {} liberada (carrinho {})", product.getCode(), cartCode);
		}
		return true;
	}

	@Override
	public void handleExternalRemoval(final SeatHoldModel hold)
	{
		final ProductModel product = hold.getProduct();
		if (product == null)
		{
			return;
		}
		final PK productPk = product.getPk();
		final PK warehousePk = hold.getWarehouse() == null ? null : hold.getWarehouse().getPk();
		final String cartCode = hold.getCartCode();
		final String seatCode = product.getCode();
		LOG.info("Reserva da poltrona {} apagada fora do servi\u00e7o (carrinho {}): liberando a poltrona", seatCode, cartCode);
		final Tenant tenant = Registry.getCurrentTenant();
		final Thread thread = new Thread(() -> {
			Registry.setCurrentTenant(tenant);
			try
			{
				Thread.sleep(EXTERNAL_REMOVAL_DELAY_MS);
				sessionService.executeInLocalView(new SessionExecutionBody()
				{
					@Override
					public void executeWithoutResult()
					{
						userService.setCurrentUser(userService.getAdminUser());
						SeatHoldRemovalContext.runInternal(() -> cleanupExternalRemoval(productPk, warehousePk, cartCode));
					}
				});
			}
			catch (final InterruptedException e)
			{
				Thread.currentThread().interrupt();
			}
			catch (final Exception e)
			{
				LOG.error("Falha ao liberar a poltrona " + seatCode + " (carrinho " + cartCode + ")", e);
			}
			finally
			{
				Registry.unsetCurrentTenant();
			}
		}, "matchday-reserva-apagada-" + seatCode);
		thread.setDaemon(true);
		thread.start();
	}

	/**
	 * Devolve a unidade a venda (se ninguem a reservou de novo) e tira a
	 * poltrona do carrinho pelo servico de carrinho, que recalcula o total.
	 */
	protected void cleanupExternalRemoval(final PK productPk, final PK warehousePk, final String cartCode)
	{
		final ProductModel seat = modelService.get(productPk);
		if (findHold(seat) != null)
		{
			LOG.info("Poltrona {} j\u00e1 foi reservada de novo; nada a liberar", seat.getCode());
			return;
		}
		if (warehousePk != null)
		{
			final WarehouseModel warehouse = modelService.get(warehousePk);
			final StockLevelModel level = stockService.getStockLevel(seat, warehouse);
			if (level != null)
			{
				modelService.refresh(level);
				if (level.getReserved() > 0)
				{
					stockService.release(seat, warehouse, 1, "matchday:reserva apagada " + cartCode);
				}
			}
		}
		final CartModel cart = findCart(cartCode);
		if (cart == null || cart.getEntries() == null)
		{
			LOG.info("Poltrona {} liberada; carrinho {} n\u00e3o existe mais", seat.getCode(), cartCode);
			return;
		}
		// recalculo com o site, o usuario e a moeda do carrinho (preco e promocoes de socio)
		if (cart.getSite() != null)
		{
			baseSiteService.setCurrentBaseSite(cart.getSite(), true);
		}
		if (cart.getUser() != null)
		{
			userService.setCurrentUser(cart.getUser());
		}
		if (cart.getCurrency() != null)
		{
			commonI18NService.setCurrentCurrency(cart.getCurrency());
		}
		final List<Long> entryNumbers = new ArrayList<>();
		for (final AbstractOrderEntryModel entry : cart.getEntries())
		{
			if (seat.equals(entry.getProduct()) && entry.getEntryNumber() != null)
			{
				entryNumbers.add(Long.valueOf(entry.getEntryNumber().longValue()));
			}
		}
		// do fim para o comeco: tirar uma linha renumera as seguintes
		entryNumbers.sort(Comparator.reverseOrder());
		for (final Long entryNumber : entryNumbers)
		{
			final CommerceCartParameter parameter = new CommerceCartParameter();
			parameter.setEnableHooks(true);
			parameter.setCart(cart);
			parameter.setEntryNumber(entryNumber.longValue());
			parameter.setQuantity(0);
			try
			{
				commerceCartService.updateQuantityForCartEntry(parameter);
			}
			catch (final CommerceCartModificationException e)
			{
				LOG.warn("N\u00e3o foi poss\u00edvel tirar a poltrona {} do carrinho {}: {}", seat.getCode(), cartCode,
						e.getMessage());
			}
		}
		LOG.info("Poltrona {} liberada e retirada do carrinho {}", seat.getCode(), cartCode);
	}

	protected CartModel findCart(final String cartCode)
	{
		if (cartCode == null)
		{
			return null;
		}
		final FlexibleSearchQuery query = new FlexibleSearchQuery(FIND_CART);
		query.addQueryParameter("code", cartCode);
		final List<CartModel> result = flexibleSearchService.<CartModel> search(query).getResult();
		return result.isEmpty() ? null : result.get(0);
	}

	protected SeatHoldModel findHold(final ProductModel seat)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery(FIND_BY_PRODUCT);
		query.addQueryParameter("product", seat);
		final List<SeatHoldModel> result = flexibleSearchService.<SeatHoldModel> search(query).getResult();
		return result.isEmpty() ? null : result.get(0);
	}

	protected StockLevelModel findStockLevel(final ProductModel product)
	{
		final Collection<StockLevelModel> levels = stockService.getAllStockLevels(product);
		if (levels == null || levels.isEmpty())
		{
			return null;
		}
		final String preferred = configurationService.getConfiguration().getString(FlamengomatchdayConstants.WAREHOUSE,
				"warehouse_s");
		for (final StockLevelModel level : levels)
		{
			if (level.getWarehouse() != null && preferred.equals(level.getWarehouse().getCode()))
			{
				return level;
			}
		}
		return levels.iterator().next();
	}

	protected boolean isOwner(final SeatHoldModel hold, final AbstractOrderModel cart)
	{
		return cart.getCode().equals(hold.getCartCode()) || isSameRegisteredUser(hold.getUser(), cart.getUser());
	}

	protected boolean isSameRegisteredUser(final UserModel a, final UserModel b)
	{
		return a != null && b != null && a.equals(b) && !userService.isAnonymousUser(a);
	}

	protected Date newExpiry()
	{
		final int minutes = configurationService.getConfiguration().getInt(FlamengomatchdayConstants.HOLD_MINUTES, 15);
		return new Date(System.currentTimeMillis() + minutes * 60_000L);
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	public void setStockService(final StockService stockService)
	{
		this.stockService = stockService;
	}

	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}

	public void setCommerceCartService(final CommerceCartService commerceCartService)
	{
		this.commerceCartService = commerceCartService;
	}

	public void setSessionService(final SessionService sessionService)
	{
		this.sessionService = sessionService;
	}

	public void setCommonI18NService(final CommonI18NService commonI18NService)
	{
		this.commonI18NService = commonI18NService;
	}

	public void setBaseSiteService(final BaseSiteService baseSiteService)
	{
		this.baseSiteService = baseSiteService;
	}
}
