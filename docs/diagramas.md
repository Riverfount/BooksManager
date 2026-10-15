# Books Manager: Diagramas

> Diagrama de classes das entidades de domínio e seus relacionamentos.

## Diagrama de classes

```mermaid
classDiagram
    direction LR
    class Category {
        Long id
        String name
        boolean active
    }
    class Author {
        Long id
        String name
        boolean active
    }
    class Book {
        Long id
        String isbn
        String title
        String publisher
        Integer publicationYear
        boolean active
    }
    class Copy {
        Long id
        String assetCode
        CopyStatus status
        boolean active
        Long version
    }
    class Reader {
        Long id
        String name
        String document
        String email
        String phone
        LocalDate birthDate
        Guardian guardian
        boolean active
        boolean blocked
        LocalDate suspendedUntil
    }
    class Guardian {
        String name
        String email
        String phone
    }
    class Loan {
        Long id
        LocalDate pickupDate
        LocalDate dueDate
        LocalDate returnDate
        Integer renewals
    }
    class Fine {
        Long id
        BigDecimal amount
        FineStatus status
        String waiverReason
    }
    class Reservation {
        Long id
        LocalDate reservationDate
        ReservationStatus status
    }
    class User {
        Long id
        String login
        String passwordHash
        Role role
        boolean active
    }
    class LibraryConfiguration {
        Long id
        PenaltyPolicy penaltyPolicy
        BigDecimal dailyFineAmount
        BigDecimal fineCap
        Integer suspensionDaysPerDay
        Integer suspensionCapDays
        Integer loanPeriodDays
        Integer loanLimit
        Integer renewalLimit
        Integer reservationPickupDays
    }
    Book "*" -- "*" Author : written by
    Category "1" --> "*" Book : classifies
    Book "1" --> "*" Copy : has
    Copy "1" --> "*" Loan : registers
    Reader "1" --> "0..1" Guardian : has (if under 18)
    Reader "1" --> "*" Loan : performs
    Loan "1" --> "0..1" Fine : generates
    Reader "1" --> "*" Reservation : makes
    Book "1" --> "*" Reservation : receives
```

## Enumerações

| Tipo | Valores |
| --- | --- |
| `CopyStatus` | AVAILABLE, BORROWED, RESERVED, DAMAGED, LOST |
| `FineStatus` | PENDING, PAID, WAIVED |
| `ReservationStatus` | ACTIVE, FULFILLED, CANCELLED, EXPIRED |
| `Role` | ADMIN, OPERATOR |
| `PenaltyPolicy` | FINE, SUSPENSION, BOTH, NONE |

## Estado de implementação

| Entidade | Módulo | Situação |
| --- | --- | --- |
| `Category`, `Author`, `Book`, `Copy` | `catalog` | Implementadas: domínio, persistência, casos de uso e endpoints REST de cadastro, consulta, listagem, edição e inativação (issues #5–#16, #35–#37 e #43–#58), inclusive a pesquisa de livros com exemplares disponíveis (RF05, issue #83). |
| `Reader` | `user` | Domínio implementado (issue #89), com a migração `reader` (issue #88); faltam persistência, casos de uso e endpoints (fase 2, `docs/projeto.md`). |
| `User` | `user` | Planejada — fase 2 do plano de desenvolvimento (`docs/projeto.md`). |
| `Loan`, `Fine`, `Reservation`, `LibraryConfiguration` | `loan` | Planejadas — fases 3 e 4 do plano de desenvolvimento. |

## Referências

Atributos, descrição textual e relacionamentos de cada entidade: `docs/projeto.md`, seção "Modelo de domínio". Requisitos e regras de negócio relacionados: `docs/requisitos.md`.
