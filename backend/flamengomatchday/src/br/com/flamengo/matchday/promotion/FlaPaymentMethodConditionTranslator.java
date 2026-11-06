package br.com.flamengo.matchday.promotion;

import br.com.flamengo.matchday.enums.SocioPaymentMethod;
import de.hybris.platform.ruleengineservices.compiler.RuleCompilerContext;
import de.hybris.platform.ruleengineservices.compiler.RuleConditionTranslator;
import de.hybris.platform.ruleengineservices.compiler.RuleIrAttributeCondition;
import de.hybris.platform.ruleengineservices.compiler.RuleIrAttributeOperator;
import de.hybris.platform.ruleengineservices.compiler.RuleIrAttributeRelCondition;
import de.hybris.platform.ruleengineservices.compiler.RuleIrCondition;
import de.hybris.platform.ruleengineservices.compiler.RuleIrFalseCondition;
import de.hybris.platform.ruleengineservices.compiler.RuleIrGroupCondition;
import de.hybris.platform.ruleengineservices.compiler.RuleIrGroupOperator;
import de.hybris.platform.ruleengineservices.rao.CartRAO;
import de.hybris.platform.ruleengineservices.rao.PaymentModeRAO;
import de.hybris.platform.ruleengineservices.rule.data.RuleConditionData;
import de.hybris.platform.ruleengineservices.rule.data.RuleConditionDefinitionData;
import de.hybris.platform.ruleengineservices.rule.data.RuleParameterData;

import java.util.ArrayList;
import java.util.List;


/**
 * Condição "Forma de pagamento" do construtor de promoções (05/11, {@code fla_forma_pagamento}): o carrinho
 * vale quando a forma escolhida no checkout é a do parâmetro {@code value} (ex.: Pix à vista). Com
 * {@code incluirPlanos}=false (padrão), carrinho com plano de sócio não entra (a forma chega como
 * "PIX@PLANO", ver {@link FlaPaymentModeRaoPopulator}).
 */
public class FlaPaymentMethodConditionTranslator implements RuleConditionTranslator
{
	public static final String VALUE_PARAM = "value";
	public static final String PLANS_PARAM = "incluirPlanos";

	@Override
	public RuleIrCondition translate(final RuleCompilerContext context, final RuleConditionData condition,
			final RuleConditionDefinitionData conditionDefinition)
	{
		final String code = code(parameter(condition, VALUE_PARAM));
		if (code == null)
		{
			return new RuleIrFalseCondition();
		}
		final boolean plans = Boolean.TRUE.equals(parameter(condition, PLANS_PARAM));

		final String cartVariable = context.generateVariable(CartRAO.class);
		final String modeVariable = context.generateVariable(PaymentModeRAO.class);

		final RuleIrAttributeCondition mode = new RuleIrAttributeCondition();
		mode.setVariable(modeVariable);
		mode.setAttribute("code");
		if (plans)
		{
			final List<String> codes = new ArrayList<>();
			codes.add(code);
			codes.add(code + FlaPaymentModeRaoPopulator.PLAN_SUFFIX);
			mode.setOperator(RuleIrAttributeOperator.IN);
			mode.setValue(codes);
		}
		else
		{
			mode.setOperator(RuleIrAttributeOperator.EQUAL);
			mode.setValue(code);
		}

		// a forma de pagamento é a do carrinho avaliado
		final RuleIrAttributeRelCondition cartMode = new RuleIrAttributeRelCondition();
		cartMode.setVariable(cartVariable);
		cartMode.setAttribute("paymentMode");
		cartMode.setOperator(RuleIrAttributeOperator.EQUAL);
		cartMode.setTargetVariable(modeVariable);

		final RuleIrGroupCondition group = new RuleIrGroupCondition();
		group.setOperator(RuleIrGroupOperator.AND);
		final List<RuleIrCondition> children = new ArrayList<>();
		children.add(mode);
		children.add(cartMode);
		group.setChildren(children);
		return group;
	}

	protected Object parameter(final RuleConditionData condition, final String name)
	{
		if (condition == null || condition.getParameters() == null)
		{
			return null;
		}
		final RuleParameterData p = condition.getParameters().get(name);
		return p == null ? null : p.getValue();
	}

	protected String code(final Object value)
	{
		if (value instanceof SocioPaymentMethod)
		{
			return ((SocioPaymentMethod) value).getCode();
		}
		if (value instanceof Enum)
		{
			return ((Enum<?>) value).name();
		}
		return value == null ? null : value.toString();
	}
}
