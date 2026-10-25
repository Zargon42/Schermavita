# To revisit

## Fencing rating systems

Recorded from the rating discussion on 19 October 2026. These are future design ideas; keep the current rating formula unchanged for now.

- [ ] Revisit the difference between scoring strength and competitive success. The current touch-share model gives a 15–14 win only about +0.55 against an equally rated opponent, and a 14–15 loss about −0.55. It has no separate reward for winning the decisive touch.
- [ ] Consider a win/loss component or separate outcome and touch-share ratings. A 50/50 outcome/touch-share blend was discussed as an illustration, not an agreed or validated weighting. Do not treat a blended rating as a calibrated expected-touch model.
- [ ] Evaluate Bradley–Terry and SpringRank-like methods alongside the current model. Explore how each could use results, score margins, third-party bouts, sparse evidence, and separate weapons.
- [ ] Revisit same-day aggregation. Currently every expectation on a date uses the ratings from before that date, then each participant receives the mean of their bout adjustments. For adjustments +4, +8, and −3, the daily change is +3, not +9. This avoids fabricating an order for date-only imports, but does not give a many-bout day proportionally more influence. Consider order-independent alternatives that account for the amount of evidence.
- [ ] Validate alternatives against held-out recorded dates. Assess close wins/losses, different target scores, sparse and disconnected opponent networks, repeated opponents, provisional thresholds, uncertainty, and responsiveness to changing form.
- [ ] Keep predicted win probability and expected touch share distinct. Preserve a suitable expected-touch model for opponent-adjusted performance and life comparisons, even if a separate competitive-results rating is added.

Current reference: all fencers start at 1500 per weapon; expected touch share is `1 / (1 + 10^((opponentRating - personalRating) / 400))`; each bout contributes `32 * (actualTouchShare - expectedTouchShare)`. Date-batched mean updates use eligible recorded personal and third-party results. The constants and alternative weightings have not been fitted to this user's history. Ratings remain estimates from the recorded network, without official level labels.
