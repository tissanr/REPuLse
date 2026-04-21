// Externs for @supabase/supabase-js — prevents Closure advanced compilation
// from renaming method calls on the GoTrueClient auth sub-object.

var SupabaseAuthClient = {};
SupabaseAuthClient.signInWithOAuth = function() {};
SupabaseAuthClient.signOut = function() {};
SupabaseAuthClient.getSession = function() {};
SupabaseAuthClient.onAuthStateChange = function() {};

var SupabaseClient = {};
SupabaseClient.auth = SupabaseAuthClient;
