# Automation Exercise Fixture Set

This folder contains deterministic local HTML fixtures for a future Xealenium full E2E healing demo.

No tests were run while creating these fixtures. No Java code, Gradle configuration, or existing demo pages were changed.

## Intended Future Flow

`register -> login -> products -> add to cart -> cart -> checkout -> payment -> order placed`

## Folders

- `Baseline/`: source-state Automation Exercise pages. Public pages were downloaded from `https://automationexercise.com/`; gated or no-session pages were locally reconstructed in the same style when the downloaded DOM was not useful for deterministic local E2E healing.
- `Updated/`: drifted copies of the same pages. These keep the business meaning and important target elements, but change IDs, classes, tags, labels, and structure to create positive healing scenarios.
- `assets/`: local CSS, JavaScript, images, fonts, and fixture styling used by both fixture folders.

## Local Asset Handling

This fixture set uses a hybrid of Option A and Option B:

- Real Automation Exercise assets were mirrored locally where practical, including Bootstrap, site CSS, site JavaScript, logo images, shop images, product images, and available Font Awesome fonts.
- Every fixture page now points at local relative paths under `../assets/...` instead of requiring `/static/...`, `/get_product_picture/...`, Google Fonts, ad scripts, or other live network dependencies.
- A small deterministic fixture stylesheet is included at `assets/css/automation-exercise-fixture.css`. It keeps reconstructed pages and heavily drifted pages visually aligned with the Automation Exercise look: white content cards, orange actions, product grids, form panels, cart/checkout/payment layouts, and footer spacing.
- The pages are intended to render offline from disk. Missing or noisy third-party scripts were removed rather than preserved.

## Styled Pages

The following pages are wired to local assets and the fixture stylesheet:

- `Baseline/login.html`
- `Baseline/products.html`
- `Baseline/product_details.html`
- `Baseline/cart.html`
- `Baseline/checkout.html`
- `Baseline/payment.html`
- `Baseline/order_placed.html`
- `Baseline/account_created.html`
- `Updated/login.html`
- `Updated/products.html`
- `Updated/product_details.html`
- `Updated/cart.html`
- `Updated/checkout.html`
- `Updated/payment.html`
- `Updated/order_placed.html`
- `Updated/account_created.html`

Downloaded public pages use the mirrored live CSS/images where possible. Reconstructed pages and drifted pages are intentionally approximated with `automation-exercise-fixture.css` so they stay deterministic and visually useful for future Xealenium visual healing tests.

## Source And Reconstruction Notes

| File | Source URL | Baseline source |
| --- | --- | --- |
| `login.html` | `https://automationexercise.com/login` | Downloaded |
| `products.html` | `https://automationexercise.com/products` | Downloaded |
| `product_details.html` | `https://automationexercise.com/product_details/1` | Downloaded |
| `cart.html` | `https://automationexercise.com/view_cart` | Locally reconstructed because direct no-session cart returned an empty cart page |
| `checkout.html` | `https://automationexercise.com/checkout` | Downloaded; direct page exposed useful checkout DOM |
| `payment.html` | `https://automationexercise.com/payment` | Downloaded; direct page exposed useful payment form DOM |
| `order_placed.html` | `https://automationexercise.com/order_placed` | Locally reconstructed because direct URL returned a generic/non-confirmation page |
| `account_created.html` | `https://automationexercise.com/account_created` | Downloaded |

## Drift Summary

- `updated/login.html`: changed login labels to Account Email and Account Password, converted login email to a `contenteditable` textbox, changed Login to a role button Sign In, changed signup Name to Full Name, Email Address to Registration Email, Signup to Create Account, and converted signup name to `contenteditable`.
- `updated/products.html`: rebuilt repeated product cards into a new catalog layout, preserved product identity with `data-product-id`, retained `/product_details/{id}` hrefs, and changed Add to cart text to Add Item / Add to Basket.
- `updated/product_details.html`: changed Quantity to Qty, converted quantity into a `contenteditable` textbox, changed Add to cart to a role button Add Item, and preserved product title, price, and `data-product-id="1"`.
- `updated/cart.html`: changed table rows into card/list items, changed row classes and remove actions, preserved product identity through `data-product-id` and product detail hrefs, and changed Proceed To Checkout to Continue to Checkout.
- `updated/checkout.html`: changed checkout sections and labels to delivery/order-summary wording, changed Place Order to Confirm Order, and changed the action into a `div role="button"`.
- `updated/payment.html`: changed payment labels to Cardholder Name, Payment Card Number, Security Code, and Confirm Payment; converted several fields to `contenteditable` role textboxes; and changed the submit action to an anchor role button.
- `updated/order_placed.html`: changed Order Placed! to Order Confirmed, changed invoice/continue button structure, and added status semantics.
- `updated/account_created.html`: changed Account Created! to Profile Created and Continue to a role button Proceed.

## Important Preserved Targets

- Login and signup inputs/actions remain semantically discoverable through labels, roles, and aria labels.
- Product cards retain product names, prices, `data-product-id`, add-to-cart actions, and product detail links.
- Product detail retains Blue Top, Rs. 500, quantity meaning, and add-item action.
- Cart retains product identities, totals, remove actions, and checkout navigation.
- Checkout retains address/order summary meaning and order confirmation action.
- Payment retains cardholder, card number, security code, expiration, and payment confirmation actions.
- Confirmation pages retain order/account creation meaning with role/status and clear action targets.
