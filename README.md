# Sorare Trading Bot

An automated bot that monitors the Sorare marketplace for trading opportunities, buys undervalued cards, lists them for sale with a profit margin, and handles counter-offers.

## Features

- 🔍 Market monitoring for undervalued cards
- 💰 Automated buying and selling with configurable profit margins
- 📊 Special card notifications (jersey mints, favorite serial numbers)
- 📩 Email alerts for special cards
- 🛑 Emergency stop system to instantly halt all trading operations
- 🕒 Transaction rate limiting to prevent excessive trading
- 📝 Transaction history tracking

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

3. **Create security directories**
   ```bash
   mkdir -p security/emergency
   ```

4. **Configure the bot**
   ```bash
   cp src/main/resources/config.properties ./config.properties
   # Edit config.properties with your settings
   ```

5. **Build the project**
   ```bash
   mvn clean package
   ```

6. **Run the bot**
   ```bash
   java -jar target/sorare-trading-bot-0.1.0-jar-with-dependencies.jar
   ```

## Configuration

Edit `config.properties` with your settings:

```properties
# Ethereum Configuration
ethereum.node.url=https://mainnet.infura.io/v3/YOUR_INFURA_API_KEY

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
trading.max.transactions.per.hour=5

# Security Configuration
security.emergency.dir=./security/emergency
security.emergency.check.interval.ms=5000

# Notification Configuration (Optional)
notification.email=your_email@example.com
notification.email.enabled=true
notification.smtp.host=smtp.gmail.com
notification.smtp.port=587
notification.smtp.username=your_email@gmail.com
notification.smtp.password=your_app_password
```

## Security Features

### Emergency Stop System

The emergency stop system allows you to immediately halt all trading operations, useful in case of:
- Unusual market volatility
- API issues
- Unexpected bot behavior
- System maintenance

Emergency stops persist across bot restarts, ensuring trading doesn't resume until explicitly cleared.

Commands:
```
emergency status              - Check emergency stop status
emergency stop <reason>       - Trigger emergency stop
emergency clear               - Clear emergency stop
emergency clear-force         - Force clear emergency stop (use with caution)
```

### Transaction Rate Limiting

The bot implements a sliding-window rate limiter that prevents executing more than a configurable number of transactions per hour. This:
- Prevents excessive trading during volatile markets
- Limits potential losses if issues occur
- Reduces API rate limit issues
- Provides a more predictable trading pattern

Configure the limit in `config.properties`:
```properties
trading.max.transactions.per.hour=5
```

## Using the Bot

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

## Best Practices

### Security Best Practices

1. **Use Transaction Rate Limiting**: Start conservative with a low limit (3-5 transactions per hour)
2. **Use a Dedicated Wallet**: Always use a wallet with limited funds for the bot
3. **Test the Emergency Stop**: Regularly test that the emergency stop works as expected
4. **Monitor Logs**: Check logs regularly for warnings or errors
5. **Test on Testnet First**: Use Sepolia or other testnets before deploying to mainnet

### Operational Tips

1. **Start with Small Watchlist**: Begin with a few players you know well
2. **Conservative Limits**: Use conservative transaction size limits initially
3. **Regular Backups**: Back up your database regularly
4. **Update Dependencies**: Keep libraries up-to-date, especially web3j and security components
5. **Monitor Balance**: Keep track of your wallet balance and ETH prices

## Testing Before Deployment

1. **Use a testnet first**
   ```properties
   ethereum.node.url=https://sepolia.infura.io/v3/YOUR_KEY
   ```

2. **Set conservative limits**
   ```properties
   trading.max.eth.per.transaction=0.05
   trading.max.transactions.per.hour=3
   ```

3. **Monitor logs**
   ```
   tail -f logs/sorarebot.log
   ```

## Disclaimer

This bot involves financial transactions. Use at your own risk and always start with small amounts.