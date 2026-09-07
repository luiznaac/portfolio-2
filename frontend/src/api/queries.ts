import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "./client.ts";
import type {
  BondOrderCreation,
  CheckingAccountCreation,
  FixedRateBondCreation,
  FloatingRateBondCreation,
  IndexId,
  MovementRequest,
  UploadBroker,
  UploadProduct,
} from "./types.ts";

export const keys = {
  bonds: ["bonds"] as const,
  bondPositions: (id: number) => ["bonds", id, "positions"] as const,
  checkingAccounts: ["checking-accounts"] as const,
  checkingAccountPositions: (id: number) =>
    ["checking-accounts", id, "positions"] as const,
  indexes: ["indexes"] as const,
  indexValues: (id: IndexId) => ["indexes", id, "values"] as const,
};

// --- bonds ---

export function useBonds() {
  return useQuery({ queryKey: keys.bonds, queryFn: () => api.listBonds() });
}

export function useBondPositions(id: number) {
  return useQuery({
    queryKey: keys.bondPositions(id),
    queryFn: () => api.bondPositions(id),
  });
}

export function useCreateBond() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (
      body:
        | ({ kind: "fixed" } & FixedRateBondCreation)
        | ({ kind: "floating" } & FloatingRateBondCreation),
    ) =>
      body.kind === "fixed"
        ? api.createFixedBond(body)
        : api.createFloatingBond(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.bonds }),
  });
}

export function useCreateBondOrder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: BondOrderCreation) => api.createBondOrder(body),
    onSuccess: (_r, body) => {
      qc.invalidateQueries({ queryKey: keys.bonds });
      if (body.bond_id) {
        qc.invalidateQueries({ queryKey: keys.bondPositions(body.bond_id) });
      }
    },
  });
}

// --- checking accounts ---

export function useCheckingAccounts() {
  return useQuery({
    queryKey: keys.checkingAccounts,
    queryFn: () => api.listCheckingAccounts(),
  });
}

export function useCheckingAccountPositions(id: number) {
  return useQuery({
    queryKey: keys.checkingAccountPositions(id),
    queryFn: () => api.checkingAccountPositions(id),
  });
}

export function useCreateCheckingAccount() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CheckingAccountCreation) =>
      api.createCheckingAccount(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.checkingAccounts }),
  });
}

export function useMovement(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      kind,
      body,
    }: {
      kind: "deposit" | "withdraw" | "full-withdraw";
      body: MovementRequest;
    }) =>
      kind === "deposit"
        ? api.deposit(id, body)
        : kind === "withdraw"
          ? api.withdraw(id, body)
          : api.fullWithdraw(id, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: keys.checkingAccounts });
      qc.invalidateQueries({ queryKey: keys.checkingAccountPositions(id) });
    },
  });
}

// --- consolidation ---

export function useConsolidate() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (target: { kind: "bond" | "checking-account"; id: number }) =>
      target.kind === "bond"
        ? api.consolidateBond(target.id)
        : api.consolidateCheckingAccount(target.id),
    onSuccess: (_r, target) => {
      qc.invalidateQueries({
        queryKey:
          target.kind === "bond"
            ? keys.bondPositions(target.id)
            : keys.checkingAccountPositions(target.id),
      });
      qc.invalidateQueries({ queryKey: keys.bonds });
      qc.invalidateQueries({ queryKey: keys.checkingAccounts });
    },
  });
}

export function useScheduleConsolidations() {
  return useMutation({ mutationFn: () => api.scheduleConsolidations() });
}

// --- indexes ---

export function useIndexes() {
  return useQuery({ queryKey: keys.indexes, queryFn: () => api.listIndexes() });
}

export function useIndexValues(id: IndexId) {
  return useQuery({
    queryKey: keys.indexValues(id),
    queryFn: () => api.indexValues(id),
  });
}

export function useHydrateIndex() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: IndexId) => api.hydrateIndex(id),
    onSuccess: (_r, id) =>
      qc.invalidateQueries({ queryKey: keys.indexValues(id) }),
  });
}

// --- upload ---

export function useUploadXlsx() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: {
      broker: UploadBroker;
      product: UploadProduct;
      productId: number;
      file: Blob;
    }) => api.uploadXlsx(args.broker, args.product, args.productId, args.file),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: keys.bonds });
      qc.invalidateQueries({ queryKey: keys.checkingAccounts });
    },
  });
}
