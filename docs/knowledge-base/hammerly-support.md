# Hammerly Support Guide

This guide describes behavior implemented by the Hammerly application. It is support reference data, not a payment, shipping, refund, or legal policy.

## Accounts And Security

Create an account with a first name, last name, email address, password, and optional phone number. Email addresses must be unique. Sign in with the registered email and password. Authenticated requests use the bearer token returned by registration or login. An invalid or expired token requires signing in again.

Never share a password, bearer token, or full payment-card data in AI support chat. Hammerly AI
does not need these credentials to explain platform features.

## Profiles

Signed-in users can view and update their first name, last name, email address, and phone number. They can add or remove a profile avatar and change their password by supplying the current password. A user can also manage saved payment-method records and choose a default record; Hammerly does not currently process auction settlement, refunds, or shipping.

## Finding auctions

The marketplace shows active auction listings and basic statistics. Users can open an auction to see its title, description, condition, seller, current bid, remaining time, and bid history. Search matches auction titles. Auction categories include the category stored by the seller when the listing is created.

## Selling

A signed-in seller can create an auction with a title, category, description, starting price, condition, and duration. The seller owns the new listing. A seller can end or delete only an auction they own. Ending an auction changes its end time so it is no longer active. Hammerly does not implement automatic payment or delivery when an auction ends.

## Bidding

To place a bid, sign in, open the active auction detail page, enter an amount higher than the
current bid, and submit it. Hammerly validates the amount before saving it. The auction must still
be active, and a seller cannot bid on their own auction. A successful bid updates the current bid
and appears in bid history. Signed-in users can view their bids and whether their latest bid is
currently winning.

## Auction Lifecycle

Every auction has an end time. Bids are rejected after that time. The interface displays remaining time for active listings. When time expires, the listing is no longer available for new bids. Hammerly currently records the auction and bids but does not automatically charge a winner.

## Watchlists

Signed-in users can add an auction to their watchlist, view watched auctions, check whether an auction is watched, and remove it later. Adding the same auction twice is rejected. A watchlist is a personal bookmark and does not place a bid or reserve an item.

## Payments And Winning

Hammerly currently records auctions, bids, and the latest winning position, but it does not
automatically charge the winner, settle the auction, issue refunds, or arrange shipping. Saved
payment-method records are account data only; they are not used for automatic auction settlement.

## AI Support

The AI support chat answers questions about Hammerly features. Responses stream incrementally so text can appear before the full answer is complete. When indexed Hammerly knowledge is relevant, support retrieves matching passages and shows a compact Sources section. Conversation state and short-lived retrieval results may use Redis, while PostgreSQL with pgvector stores indexed knowledge. If retrieval is temporarily unavailable, chat can continue without citations; unsupported policies should not be invented.

## Knowledge ingestion

Internal operators can submit a knowledge document to the protected ingestion endpoint. The API stores the document and a transactional outbox event together, returning `PENDING`. The relay publishes the event to Kafka with at-least-once delivery. A worker marks the document `PROCESSING`, chunks and embeds it, atomically replaces its vector rows, and then marks it `READY`. Permanent failures are sent to a dead-letter topic and may mark the document `FAILED` with a sanitized reason.
