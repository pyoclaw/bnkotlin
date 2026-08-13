# Domain Model

## Core entities

- Restaurant
- Location
- User
- Role
- MenuCategory
- Product
- ModifierGroup
- ModifierOption
- Cart
- CartItem
- Order
- OrderItem
- Payment
- Notification
- KitchenEvent
- CustomerProfile
- DeviceSession
- PrinterJob
- OpeningHours
- OrderTimelineEvent

## Relationships

- Restaurant owns locations.
- Location owns menu availability, hours, and devices.
- Product belongs to a category.
- Product may have multiple modifier groups.
- Cart contains cart items.
- Order is created from a cart snapshot.
- Payment references an order.
- Notifications reference events or state changes.
- KitchenEvent records staff or system actions.

## Snapshot rule

Once an order is created, item snapshots must remain immutable.
