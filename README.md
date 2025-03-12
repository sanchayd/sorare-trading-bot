# Sorare Trading Bot

An automated bot that monitors the Sorare marketplace for trading opportunities, buys undervalued cards, lists them for sale with a profit margin, and handles counter-offers.

## Setup Instructions

### Prerequisites
- Java 11+
- Maven
- Ethereum wallet (keystore file)
- Sorare API token
- Infura or Alchemy API key

### Quick Start

1. **Fork the repository**

2. **Clone your forked repository**
   ```bash
   git clone https://github.com/your-username/sorare-trading-bot.git
   cd sorare-trading-bot
   ```

3. **Configure the bot**
   ```bash
   cp src/main/resources/config.properties ./config.properties
   # Edit config.properties with your settings
   ```

4. **Build the project**
   ```bash
   mvn clean package
   ```

5. **Run the bot**
   ```bash
   java -jar target/sorare-trading-bot-0.1.0-jar-with-dependencies.jar
   ```

## Configuration

Edit `config.properties` with your settings:

```properties
# Ethereum Configuration
ethereum.node.url=https://mainnet.infura.io/v3/YOUR_INFURA_API_KEY (c)

# Wallet Configuration (IMPORTANT: Use a dedicated wallet with limited funds)
wallet.path=./keystore/your-wallet-file.json
wallet.password=your_wallet_password_here

# Sorare API
sorare.api.token=your_sorare_api_token_here

# Database
db.path=./sorarebot.db

# Trading Parameters
trading.max.eth.per.transaction=0.5
trading.min.discount.percentage=15
trading.markup.percentage=5

# Notification Configuration (Optional)
notification.email=your_email@example.com
notification.email.enabled=true
notification.smtp.host=smtp.gmail.com
notification.smtp.port=587
notification.smtp.username=your_email@gmail.com
notification.smtp.password=your_app_password
```

## Customizing the Bot

### Adding Players to Watch

After starting the bot, use these commands:
```
add <player_id> <rarity>     # Add player to watchlist
remove <player_id>           # Remove player from watchlist
list                         # List watched players
```

### Special Card Notifications

```
serial add 66 limited        # Get notified about card #66/100 in limited rarity
serial add 1                 # Get notified about any card #1/X
jersey on                    # Enable jersey mint notifications
jersey price 0.5             # Only notify for jersey mints under 0.5 ETH
```

### Custom Trading Rules

To implement your own custom logic:

1. **Modify the price evaluation strategy**
   
   Edit `TradingEngine.java` and locate the `PriceEvaluator` inner class:
   ```java
   private static class PriceEvaluator {
       // Change this value to adjust discount threshold (default 15%)
       private static final BigDecimal SIGNIFICANT_DISCOUNT = new BigDecimal("0.85");
       
       public boolean isUndervalued(BigDecimal cardPrice, BigDecimal floorPrice) {
           // Your custom logic here
           return cardPrice.compareTo(thresholdPrice) < 0;
       }
   }
   ```

2. **Add custom notification rules**
   
   Extend the `checkForSpecialCard` method in `TradingEngine.java`:
   ```java
   private void checkForSpecialCard(Card card) {
       // Existing checks...
       
       // Your custom condition
       if (card.getPlayerName().contains("Messi") && card.getPrice().compareTo(BigDecimal.valueOf(1.0)) < 0) {
           notificationService.notifySpecialCard(card, "Messi card under 1 ETH!");
       }
   }
   ```

3. **Create a new repository for custom preferences**
   
   Create a class similar to `CardPreferenceRepository.java` to store your custom parameters.

4. **Update CLI commands**
   
   Add new commands to `SorareTradingBot.java` in the command line interface section.

## Testing Before Deployment

1. **Use a testnet first**
   ```properties
   ethereum.node.url=https://sepolia.infura.io/v3/YOUR_KEY
   ```

2. **Set conservative limits**
   ```properties
   trading.max.eth.per.transaction=0.05
   ```

3. **Monitor logs**
   ```
   tail -f logs/sorarebot.log
   ```

## Security Considerations

- Use a dedicated Ethereum wallet with limited funds
- Store your keystore file securely
- Never commit sensitive information to your repository
- Test thoroughly on testnet before using real funds

## Disclaimer

This bot involves financial transactions. Use at your own risk and always start with small amounts.