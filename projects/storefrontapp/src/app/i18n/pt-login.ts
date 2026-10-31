import { TranslationResources } from '@spartacus/core';

/**
 * Português do login e da saída (protótipo Flamengo).
 *
 * Formulário de login, código de verificação (login com código), "Sair" da
 * conta e as mensagens de erro de login. O título das páginas (Entrar, Página
 * inicial, Redefinir senha, Criar conta) vem do conteúdo do servidor.
 * Chunks: userAccount, common.
 */
export const ptLogin: TranslationResources = {
  pt: {
    userAccount: {
      loginForm: {
        forgotPassword: 'Esqueceu a senha?',
        signIn: 'Entrar',
        register: 'Criar conta',
        dontHaveAccount: 'Ainda não tem conta?',
        guestCheckout: 'Comprar sem cadastro',
        emailAddress: {
          label: 'E-mail',
          placeholder: 'Digite seu e-mail',
        },
        password: {
          label: 'Senha',
          placeholder: 'Digite sua senha',
        },
        wrongEmailFormat: 'E-mail em formato inválido.',
      },
      verificationTokenForm: {
        createVerificationToken:
          'Enviamos um código de verificação para {{target}}. Confira e digite o código.',
        needInputCredentials: 'Digite seus dados para continuar.',
        sendRateLime: 'em {{waitTime}} segundos',
        resend: 'Reenviar',
        verificationCode: {
          label: 'Código de verificação',
          placeholder: 'Digite o código de verificação',
        },
        noReceiveCode: 'Não recebeu o código?',
        verify: 'Confirmar',
        back: 'Voltar',
        tokenInputHint:
          'Você pode pedir um novo código. Um contador mostra quantos segundos faltam para pedir de novo.',
      },
      verificationTokenDialog: {
        title: 'Não recebeu o código',
        noReceiveCode: 'Se você não recebeu o código, pode ser que:',
        contentLine1: '1. O e-mail ainda não chegou.',
        contentLine2: '2. O e-mail foi parar na caixa de spam.',
        contentLine3: '3. O e-mail ou a senha digitados estão incorretos.',
        close: 'Fechar',
      },
      myAccountV2User: {
        signOut: 'Sair',
      },
    },
    common: {
      httpHandlers: {
        // a mensagem do servidor vem em inglês ("Bad credentials"); aqui ela é substituída
        badRequest: {
          bad_credentials: 'E-mail ou senha incorretos. Tente de novo.',
          user_is_disabled:
            'Este usuário está desativado. Fale com o atendimento.',
        },
        badRequestPleaseLoginAgain:
          'E-mail ou senha incorretos. Tente de novo.',
        sessionExpired: 'Sua sessão expirou. Entre de novo.',
        unauthorized: {
          common: 'Não foi possível confirmar seu acesso. Entre de novo.',
          invalid_client: 'Não foi possível confirmar o acesso da loja.',
        },
      },
    },
  },
};
