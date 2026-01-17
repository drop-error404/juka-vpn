# Juka VPN - Instruções de Configuração

## 📋 Pré-requisitos

- Android Studio (versão mais recente recomendada)
- JDK 17+
- Android SDK 34
- NDK (para libs nativas)

---

## 📦 Bibliotecas Necessárias

### 1. libv2ray.aar (V2Ray Core)

O ficheiro `libv2ray.aar` é essencial para o funcionamento do V2Ray. Você precisa compilar ou baixar.

#### Opção A: Baixar pré-compilado
1. Acesse: https://github.com/AnyKernel3/AnyKernel3/releases (ou repositórios semelhantes)
2. Procure por `libv2ray` releases
3. Baixe o arquivo `.aar` mais recente
4. Coloque em: `app/libs/libv2ray.aar`

#### Opção B: Compilar do código fonte
```bash
# Clone o repositório
git clone https://github.com/AnyKernel3/AnyKernel3.git

# Siga as instruções do README para compilar
# O output será um arquivo .aar
```

#### Opção C: Usar AndroidLibV2rayLite
```bash
git clone https://github.com/AnyKernel3/AnyKernel3.git
cd AnyKernel3

# Instalar Go (necessário)
# https://golang.org/dl/

# Instalar gomobile
go install golang.org/x/mobile/cmd/gomobile@latest
gomobile init

# Compilar
make android
```

**Nota:** O caminho esperado é `app/libs/libv2ray.aar`

---

### 2. JSch (SSH Tunneling)

Já está configurado no `build.gradle`:
```gradle
implementation("com.jcraft:jsch:0.1.55")
```

Será baixado automaticamente pelo Gradle.

---

### 3. Bandeiras dos Países

As bandeiras podem ser adicionadas de duas formas:

#### Opção A: Emoji (já implementado)
O código usa emojis de bandeira via `CountryUtils.getFlagEmoji()`. Funciona automaticamente.

#### Opção B: Imagens PNG
1. Baixe bandeiras de: https://flagpedia.net/download/api
2. Renomeie para: `flag_xx.png` (ex: `flag_br.png`, `flag_us.png`)
3. Coloque em: `app/src/main/res/drawable/`

**Tamanhos recomendados:**
- mdpi: 24x16 px
- hdpi: 36x24 px
- xhdpi: 48x32 px
- xxhdpi: 72x48 px
- xxxhdpi: 96x64 px

---

## 🔧 Estrutura de Pastas

```
app/
├── libs/
│   └── libv2ray.aar          ← VOCÊ PRECISA ADICIONAR
├── src/main/
│   ├── kotlin/com/julogic/jukavpn/
│   │   ├── config/           ✓ Criado
│   │   ├── data/             ✓ Criado
│   │   ├── models/           ✓ Criado
│   │   ├── parsers/          ✓ Criado
│   │   ├── receiver/         ✓ Criado
│   │   ├── service/          ✓ Criado
│   │   ├── tunnel/           ✓ Criado
│   │   ├── utils/            ✓ Criado
│   │   ├── JukaVpnApplication.kt  ✓ Criado
│   │   ├── MainActivity.kt   (existente - precisa atualizar UI)
│   │   └── V2rayVpnService.kt     ✓ Atualizar conforme necessário
│   └── res/
│       ├── drawable/         ✓ Ícones criados
│       ├── layout/           (você cria as layouts)
│       ├── values/           ✓ Strings criados
│       └── xml/              ✓ Configs criados
```

---

## 🚀 Passos para Executar

### 1. Clonar/Abrir o Projeto
```bash
# Abra no Android Studio
File > Open > [selecione a pasta do projeto]
```

### 2. Adicionar libv2ray.aar
```bash
# Copie o arquivo para:
cp /caminho/para/libv2ray.aar app/libs/
```

### 3. Sincronizar Gradle
```bash
# No Android Studio:
File > Sync Project with Gradle Files

# Ou via terminal:
./gradlew build
```

### 4. Executar
- Conecte um dispositivo Android ou inicie um emulador
- Clique em "Run" (▶️)

---

## ⚠️ Problemas Comuns

### Erro: "Cannot find libv2ray"
**Solução:** Verifique se `libv2ray.aar` está em `app/libs/`

### Erro: "VPN permission denied"
**Solução:** O app precisa de permissão VPN do Android. A primeira conexão pedirá permissão.

### Erro: "minSdk 24 required"
**Solução:** O projeto requer Android 7.0+ (API 24). Altere o dispositivo/emulador.

### Erro com JSch
**Solução:** Limpe o cache do Gradle:
```bash
./gradlew clean
./gradlew build
```

---

## 📱 Funcionalidades Implementadas

### ✅ Prontas para Uso
- [x] Modelos de dados (Server, VpnProfile, etc.)
- [x] Parsers para VMess, VLESS, Shadowsocks, Trojan, SSH
- [x] Gerador de configuração V2Ray
- [x] Gerenciador de túnel SSH
- [x] Relay UDP
- [x] Repositório de servidores
- [x] Import/Export de configurações
- [x] Gerenciador de subscrições
- [x] Teste de latência
- [x] Utilitários de países/bandeiras
- [x] Sistema de notificações
- [x] Quick Settings Tile
- [x] Receivers (boot, ações)
- [x] Connection Manager

### 🔨 Você Precisa Implementar
- [ ] UI/Layouts (MainActivity, ServerListFragment, etc.)
- [ ] Integração completa com libv2ray no VpnService
- [ ] Scanner QR Code (opcional)
- [ ] Splash Screen
- [ ] Onboarding

---

## 📚 Referências

- [V2Ray Documentation](https://www.v2fly.org/)
- [JSch Documentation](http://www.jcraft.com/jsch/)
- [Android VPN Service](https://developer.android.com/reference/android/net/VpnService)

---

## 🔐 Segurança

1. **Nunca** commite chaves de assinatura no repositório
2. Use variáveis de ambiente ou `local.properties` para secrets
3. Teste em dispositivos reais antes de publicar
4. Considere usar ProGuard/R8 para release builds

---

## 📞 Suporte

Se encontrar problemas, verifique:
1. Logs do Android Studio (Logcat)
2. Console de build do Gradle
3. Versões das dependências

Boa sorte com o desenvolvimento! 🚀
