export interface DashboardQuickLink {
  label: string;
  routerLink: string[];
  queryParams?: Record<string, string>;
}