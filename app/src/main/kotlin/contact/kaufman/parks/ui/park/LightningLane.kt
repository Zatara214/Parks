package contact.kaufman.parks.ui.park

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import contact.kaufman.parks.domain.LightningLaneOffer
import contact.kaufman.parks.domain.Resort
import contact.kaufman.parks.ui.components.OfficialApps

/**
 * What it costs to skip the queues here today.
 *
 * Deliberately a quiet reference card rather than a call to action. Parks cannot sell
 * these and has no account to sell them against, so the useful thing it can do is answer
 * "what is Multi Pass today?" without opening the official app — and then say plainly
 * where to actually buy it.
 *
 * Drawn only when there is something to say. Universal's schedule carries no purchases,
 * so this never appears at USF, Islands of Adventure or Epic Universe, the same way a
 * Universal ride row has no forecast to expand.
 */
@Composable
fun LightningLaneCard(
    offers: List<LightningLaneOffer>,
    resort: Resort,
    modifier: Modifier = Modifier,
) {
    if (offers.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Lightning Lane today",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )

            offers.forEach { offer -> OfferRow(offer) }

            // Two separate warnings, and both earn their place. Disney reprices these
            // through the day, so a number read at breakfast is not a promise at noon;
            // and the purchase itself needs a logged-in account this app deliberately
            // does not have.
            Text(
                text = "Posted for today and repriced through the day. " +
                    "Buying needs your ${OfficialApps.appName(resort)} account.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OfferRow(offer: LightningLaneOffer) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = offer.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        Text(
            text = offer.rightHandText(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            // Sold out is a state, not a price, so it reads as secondary text rather
            // than sitting in the same weight and colour as a number you can act on.
            color = if (offer.available == false) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/**
 * The number on the right, or the reason there isn't one.
 *
 * Three genuinely different cases, and flattening them would each time claim something
 * the feed did not say: sold out (upstream said false), a price (upstream said a price),
 * and no answer at all (upstream gave neither). The last renders as an em dash rather
 * than "Free" or a blank gap.
 */
private fun LightningLaneOffer.rightHandText(): String = when {
    available == false -> "Sold out"
    price != null -> price
    else -> "—"
}
