package com.twocents.mobile.ui.startup

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twocents.mobile.ui.common.TwoCentsLogo
import com.twocents.mobile.ui.theme.Background
import com.twocents.mobile.ui.theme.Gold

private data class StartupQuote(val text: String, val author: String)

private val STARTUP_QUOTES = listOf(
    StartupQuote("It doesn’t matter if you’re black or white… the only color that really matters is green.", "Family Guy"),
    StartupQuote("Money is not the most important thing in the world. Love is. Fortunately, I love money.", "Jackie Mason"),
    StartupQuote("Money is the opposite of the weather. Nobody talks about it, but everybody does something about it.", "Rebecca Johnson"),
    StartupQuote("The safest way to double your money is to fold it over and put it in your pocket.", "Kin Hubbard"),
    StartupQuote("Dogs have no money. Isn’t that amazing? They’re broke their entire lives. But they get through. You know why dogs have no money? No pockets.", "Jerry Seinfeld"),
    StartupQuote("Anyone who lives within their means suffers from a lack of imagination.", "Oscar Wilde"),
    StartupQuote("Inflation is when you pay fifteen dollars for the ten-dollar haircut you used to get for five dollars when you had hair.", "Sam Ewing"),
    StartupQuote("Always borrow money from a pessimist; he doesn’t expect to be paid back.", "Unknown"),
    StartupQuote("Too many people spend money they haven’t earned, to buy things they don’t want, to impress people they don’t like.", "Will Smith"),
    StartupQuote("I am having an out-of-money experience.", "Author Unknown"),
    StartupQuote("The economy depends about as much on economists as the weather does on weather forecasters.", "Jean-Paul Kauffmann"),
    StartupQuote("Money isn’t the most important thing in life, but it’s reasonably close to oxygen on the ‘gotta have it’ scale.", "Zig Ziglar"),
    StartupQuote("Intaxication: Euphoria at getting a refund from the IRS, which lasts until you realize it was your money to start with.", "Washington Post word contest"),
    StartupQuote("What’s worth doing is worth doing for money.", "Gordon Gekko (Wall Street)"),
    StartupQuote("Money’s only something you need in case you don’t die tomorrow.", "Carl Fox (Wall Street)"),
    StartupQuote("The rich. You know why they’re so odd? Because they can afford to be.", "Alexander Knox (Batman)"),
    StartupQuote("Money, it turned out, was exactly like sex; you thought of nothing else if you didn’t have it and thought of other things if you did.", "James Baldwin"),
    StartupQuote("The only reason I made a commercial for American Express was to pay for my American Express bill.", "Peter Ustinov"),
    StartupQuote("I’m spending a year dead for tax reasons.", "Douglas Adams"),
    StartupQuote("You should always live within your income, even if you have to borrow to do so.", "Josh Billings"),
    StartupQuote("If there is anyone to whom I owe money, I’m prepared to forget it if they are.", "Errol Flynn"),
    StartupQuote("I have enough money to last me the rest of my life, unless I buy something.", "Jackie Mason"),
    StartupQuote("Carpe per diem – seize the check.", "Robin Williams"),
    StartupQuote("I made my money the old-fashioned way. I was very nice to a wealthy relative right before he died.", "Malcolm Forbes"),
    StartupQuote("It’s money. I remember it from when I was single.", "Billy Crystal"),
)

@Composable
fun StartupScreen(progress: Float) {
    val quote = remember { STARTUP_QUOTES.random() }
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 260),
        label = "startup-progress",
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TwoCentsLogo(
            modifier = Modifier.width(180.dp).height(44.dp),
            alignment = Alignment.Center,
        )
        Spacer(Modifier.height(24.dp))
        androidx.compose.foundation.layout.Box(
            Modifier.width(180.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.08f)),
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxWidth(animatedProgress).fillMaxHeight().background(Gold.copy(alpha = 0.85f)),
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = "“${quote.text}”",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 14.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "— ${quote.author}",
            color = Gold.copy(alpha = 0.7f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}
