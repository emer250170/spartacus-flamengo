import { TranslationResources } from '@spartacus/core';

/**
 * Português da criação de conta e da redefinição de senha (protótipo Flamengo).
 *
 * Telas /login/register, /login/forgot-password e /login/pw/change, além das
 * regras de senha e do "mostrar senha" usados nesses formulários. Tratamentos
 * (Sr., Sra.…), textos de consentimento e títulos das páginas vêm do conteúdo
 * do servidor.
 * Chunks: userProfile, common.
 */
export const ptCadastroSenha: TranslationResources = {
  pt: {
    userProfile: {
      register: {
        confirmPassword: {
          action: 'Confirmar senha',
          label: 'Confirmar senha',
          placeholder: 'Repita a senha',
        },
        managementInMyAccount: 'Gerencie em Minha conta.',
        termsAndConditions: 'Termos e Condições',
        signIn: 'Já tenho conta. Entrar',
        register: 'Criar conta',
        confirmNewPassword: 'Confirmar nova senha',
        resetPassword: 'Redefinir senha',
        createAccount: 'Criar conta',
        title: 'Tratamento',
        titleOptional: 'Tratamento (opcional)',
        firstName: {
          label: 'Nome',
          placeholder: 'Nome',
        },
        lastName: {
          label: 'Sobrenome',
          placeholder: 'Sobrenome',
        },
        emailAddress: {
          label: 'E-mail',
          placeholder: 'E-mail',
        },
        password: {
          label: 'Senha',
          placeholder: 'Senha',
        },
        newPassword: 'Nova senha',
        emailMarketing:
          'Usar meus dados pessoais para receber newsletters e campanhas por e-mail. Para mudar, acesse Consentimentos em Minha conta.',
        confirmThatRead: 'Confirmo que li e concordo com os',
        selectTitle: 'Selecione',
        passwordMinRequirements:
          'Dica para uma senha segura: use pelo menos 8 caracteres, misturando letras maiúsculas e minúsculas, números e símbolos.',
        passwordStrengthRequirements:
          'Dica para uma senha segura: use pelo menos 8 caracteres, misturando letras maiúsculas e minúsculas, números e símbolos, sem repetir o mesmo caractere em sequência.',
        bothPasswordMustMatch: 'As duas senhas precisam ser iguais',
        titleRequired: 'Escolha o tratamento.',
        postRegisterMessage: 'Entre com o e-mail e a senha que você cadastrou.',
        postRegisterSuccessMessage:
          'Conta criada! Entre com o e-mail e a senha que você cadastrou.',
      },
      forgottenPassword: {
        resetPassword: 'Redefinir senha',
        enterEmailAddressAssociatedWithYourAccount:
          'Digite o e-mail da sua conta',
        emailAddress: {
          label: 'E-mail',
          placeholder: 'Digite seu e-mail',
        },
        enterValidEmail: 'Digite um e-mail válido.',
        passwordResetEmailSent:
          'Enviamos um e-mail com as instruções para redefinir a sua senha.',
        passwordResetSuccess:
          'Pronto! Agora você já pode entrar com a nova senha.',
      },
      updateEmailForm: {
        newEmailAddress: {
          label: 'Novo e-mail',
          placeholder: 'Digite o e-mail',
        },
        confirmNewEmailAddress: {
          label: 'Confirmar novo e-mail',
          placeholder: 'Digite o e-mail',
        },
        enterValidEmail: 'Digite um e-mail válido.',
        bothEmailMustMatch: 'Os dois e-mails precisam ser iguais',
        password: {
          label: 'Senha',
          placeholder: 'Digite a senha',
        },
        pleaseInputPassword: 'Digite a senha',
        emailUpdateSuccess: 'Pronto! Entre com {{ newUid }}.',
      },
    },
    common: {
      passwordVisibility: {
        showPassword: 'Mostrar senha',
        hidePassword: 'Ocultar senha',
      },
      formErrors: {
        labeled: {
          cxInvalidEmail:
            'O campo {{label}} precisa ser um e-mail válido, no formato exemplo@seudominio.com',
          cxInvalidPassword: 'O campo {{label}} não tem uma senha válida',
          cxMinOneUpperCaseCharacter:
            'A senha precisa ter pelo menos uma letra maiúscula',
          cxMinOneDigit: 'A senha precisa ter pelo menos um número',
          cxMinOneSpecialCharacter:
            'A senha precisa ter pelo menos um símbolo (ex.: ! @ # $)',
          cxMinSixCharactersLength:
            'A senha precisa ter pelo menos 6 caracteres',
          cxMinEightCharactersLength:
            'A senha precisa ter pelo menos 8 caracteres',
          cxMaxCharactersLength: 'A senha pode ter no máximo 128 caracteres',
          cxContainsSpecialCharacters: 'A senha não pode ter símbolos',
          cxNoConsecutiveCharacters:
            'A senha não pode repetir o mesmo caractere em sequência',
        },
        cxInvalidPassword: 'Senha em formato inválido',
        cxPasswordsMustMatch: 'As senhas precisam ser iguais',
        cxPasswordsCannotMatch: 'A nova senha precisa ser diferente da atual',
        cxEmailsMustMatch: 'Os e-mails precisam ser iguais',
      },
    },
  },
};
